package com.example.waystoneinjector.client;

import com.example.waystoneinjector.config.NetherPortalVariant;
import com.example.waystoneinjector.config.WaystoneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

/**
 * Applies the configured nether portal texture pack by enabling/disabling the corresponding
 * built-in resource pack entry in the client's options.
 */
@OnlyIn(Dist.CLIENT)
public class NetherPortalPackApplier {

    private static final String PACK_PREFIX = "waystoneinjector_nether_portal_";

    private static boolean hasAppliedOnce = false;
    private static int tickCount = 0;
    private static final int DELAY_TICKS = 120; // wait a few seconds for options + pack repo to be ready

    private static String lastApplied = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCount++;
        if (!hasAppliedOnce && tickCount < DELAY_TICKS) {
            return;
        }

        applyIfChanged();
        hasAppliedOnce = true;
    }

    private static void applyIfChanged() {
        NetherPortalVariant variant = WaystoneConfig.NETHER_PORTAL_VARIANT.get();
        if (variant == null) {
            variant = NetherPortalVariant.OFF;
        }

        String variantKey = variant.name().toLowerCase();

        // Avoid spamming reloads; apply only when the config value changes.
        if (variantKey.equals(lastApplied)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }

        boolean changed = applyToRepositoryAndOptions(mc, variant);
        if (changed) {
            try {
                mc.options.save();
            } catch (Exception ignored) {
                // If options can't be saved for any reason, still try to reload packs.
            }

            try {
                mc.reloadResourcePacks();
            } catch (Exception ignored) {
                // If reload isn't available in this environment, do nothing.
            }
        }

        lastApplied = variantKey;
    }

    private static boolean applyToRepositoryAndOptions(Minecraft mc, NetherPortalVariant variant) {
        String variantKey = variant.name().toLowerCase();
        String desiredId = variant == NetherPortalVariant.OFF ? null : (PACK_PREFIX + variantKey);

        // Read current selection from the repository (this is what actually controls enabled packs).
        PackRepository repo = mc.getResourcePackRepository();
        List<String> currentSelected = new ArrayList<>(repo.getSelectedIds());
        List<String> nextSelected = new ArrayList<>();

        for (String id : currentSelected) {
            if (id == null) continue;
            if (!id.startsWith(PACK_PREFIX)) {
                nextSelected.add(id);
            }
        }

        if (desiredId != null && !desiredId.isBlank()) {
            // Put ours at the end so it has highest priority.
            nextSelected.add(desiredId);
        }

        boolean changed = !nextSelected.equals(currentSelected);

        if (changed) {
            try {
                repo.setSelected(nextSelected);
            } catch (Exception e) {
                // If the repository can't be updated for any reason, still try to persist to options.
            }
        }

        // Also mirror into options so it persists across restarts.
        List<String> currentOptions = new ArrayList<>(getSelectedResourcePacks(mc.options));
        List<String> next = new ArrayList<>();

        // Remove any previously-enabled nether portal packs.
        for (String id : currentOptions) {
            if (id == null) continue;
            if (!id.startsWith(PACK_PREFIX)) {
                next.add(id);
            }
        }

        if (desiredId != null && !desiredId.isBlank()) {
            next.add(desiredId);
        }

        if (next.equals(currentOptions)) {
            // Even if options are unchanged, repository may have changed (or vice-versa).
            // We consider this a change if either side changed.
            return changed;
        }

        setSelectedResourcePacks(mc.options, next);
        System.out.println("[WaystoneInjector] Nether portal pack set to: " + variantKey);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getSelectedResourcePacks(Object options) {
        try {
            Field field = findField(options.getClass(), "resourcePacks");
            if (field == null) return java.util.Collections.emptyList();
            field.setAccessible(true);
            Object value = field.get(options);
            return value instanceof List ? (List<String>) value : java.util.Collections.emptyList();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static void setSelectedResourcePacks(Object options, List<String> packs) {
        try {
            Field field = findField(options.getClass(), "resourcePacks");
            if (field == null) return;
            field.setAccessible(true);
            Object value = field.get(options);
            if (value instanceof List) {
                List<String> list = (List<String>) value;
                list.clear();
                list.addAll(packs);
            }
        } catch (Exception ignored) {
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
