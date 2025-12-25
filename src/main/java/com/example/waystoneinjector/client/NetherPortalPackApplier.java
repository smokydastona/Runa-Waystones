package com.example.waystoneinjector.client;

import com.example.waystoneinjector.config.WaystoneConfig;
import net.minecraft.client.Minecraft;
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
        String variant = WaystoneConfig.NETHER_PORTAL_VARIANT.get();
        if (variant == null) {
            variant = "off";
        }
        variant = variant.trim().toLowerCase();

        // Avoid spamming reloads; apply only when the config value changes.
        if (variant.equals(lastApplied)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }

        boolean changed = applyToOptions(mc, variant);
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

        lastApplied = variant;
    }

    private static boolean applyToOptions(Minecraft mc, String variant) {
        List<String> current = new ArrayList<>(getSelectedResourcePacks(mc.options));
        List<String> next = new ArrayList<>();

        // Remove any previously-enabled nether portal packs.
        for (String id : current) {
            if (id == null) continue;
            if (!id.startsWith(PACK_PREFIX)) {
                next.add(id);
            }
        }

        if (!variant.equals("off") && !variant.isBlank()) {
            String desired = PACK_PREFIX + variant;
            // Put ours at the end so it has highest priority.
            next.add(desired);
        }

        if (next.equals(current)) {
            return false;
        }

        setSelectedResourcePacks(mc.options, next);
        System.out.println("[WaystoneInjector] Nether portal pack set to: " + variant);
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
