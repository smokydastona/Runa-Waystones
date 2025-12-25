package com.example.waystoneinjector.client;

import com.example.waystoneinjector.WaystoneInjectorMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Registers built-in resource packs that can override vanilla nether portal textures.
 *
 * These packs are generated under src/main/resources/resourcepacks/ and shipped inside the mod jar.
 */
public class NetherPortalResourcePacks {

    private static final String PACKS_ROOT = "resourcepacks";
    private static final String PACK_PREFIX = "waystoneinjector_nether_portal_";

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        var modFile = ModList.get().getModFileById(WaystoneInjectorMod.MODID).getFile();

        // We ship one built-in pack per sharestone color.
        // Keep this list in sync with the generated resourcepacks folder names.
        String[] colors = new String[] {
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
            "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow"
        };

        event.addRepositorySource(consumer -> {
            for (String color : colors) {
                String packId = PACK_PREFIX + color;
                Path packPath = Objects.requireNonNull(modFile.findResource(PACKS_ROOT, packId), "Missing built-in pack folder: " + packId);

                Component title = Objects.requireNonNull(Component.literal("WaystoneInjector Nether Portal (" + color + ")"));
                PackSource source = Objects.requireNonNull(PackSource.BUILT_IN);

                Pack pack = Pack.readMetaAndCreate(
                    packId,
                    title,
                    false,
                    id -> new PathPackResources(id, packPath, false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    source
                );

                if (pack != null) {
                    consumer.accept(pack);
                }
            }
        });
    }
}
