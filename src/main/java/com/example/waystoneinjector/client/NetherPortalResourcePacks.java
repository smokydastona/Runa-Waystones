package com.example.waystoneinjector.client;

import com.example.waystoneinjector.WaystoneInjectorMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.IModFile;

import java.nio.file.Path;

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

        IModFile modFile = ModList.get().getModFileById(WaystoneInjectorMod.MODID).getFile();

        // We ship one built-in pack per sharestone color.
        // Keep this list in sync with the generated resourcepacks folder names.
        String[] colors = new String[] {
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
            "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow"
        };

        event.addRepositorySource(consumer -> {
            for (String color : colors) {
                String packId = PACK_PREFIX + color;
                Path packPath = modFile.findResource(PACKS_ROOT, packId);

                Pack pack = Pack.readMetaAndCreate(
                    packId,
                    Component.literal("WaystoneInjector Nether Portal (" + color + ")"),
                    false,
                    id -> new PathPackResources(id, packPath, false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILTIN
                );

                if (pack != null) {
                    consumer.accept(pack);
                }
            }
        });
    }
}
