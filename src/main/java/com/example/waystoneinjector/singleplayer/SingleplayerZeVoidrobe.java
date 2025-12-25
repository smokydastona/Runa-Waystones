package com.example.waystoneinjector.singleplayer;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Singleplayer-only implementation of the Ze Voidrobe.
 *
 * This runs on the integrated server thread and persists to the current world save folder,
 * so it works with only the client mod installed.
 */
@SuppressWarnings("null")
public final class SingleplayerZeVoidrobe {

    public static final int SIZE = 54;

    private SingleplayerZeVoidrobe() {
    }

    public static void open(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;

            Path file = getStorageFile(server, playerId);
            PersistentContainer container = new PersistentContainer(file);

            MenuProvider provider = new SimpleMenuProvider(
                (containerId, inv, p) -> ChestMenu.sixRows(containerId, inv, container),
                Component.literal("Ze Voidrobe")
            );

            player.openMenu(provider);
        });
    }

    private static Path getStorageFile(MinecraftServer server, UUID playerId) {
        // Prefer per-world storage.
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);

            Path primaryDir = worldRoot.resolve("data").resolve("waystoneinjector").resolve("ze_voidrobe");

            Files.createDirectories(primaryDir);

            Path primary = primaryDir.resolve(playerId.toString() + ".dat");
            return primary;
        } catch (Throwable t) {
            // Fallback: config folder (still local, but shared across worlds)
            try {
                Path primaryDir = Path.of("config", "waystoneinjector", "ze_voidrobe");

                Files.createDirectories(primaryDir);

                Path primary = primaryDir.resolve(playerId.toString() + ".dat");
                return primary;
            } catch (Throwable ignored) {
                Path primary = Path.of("waystoneinjector_ze_voidrobe_" + playerId + ".dat");
                return primary;
            }
        }
    }

    private static final class PersistentContainer extends SimpleContainer {
        private final Path file;

        private PersistentContainer(Path file) {
            super(SIZE);
            this.file = file;
            loadFromDisk();
        }

        private void loadFromDisk() {
            CompoundTag tag = tryRead(this.file);
            if (tag == null) return;

            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, items);
            for (int i = 0; i < SIZE; i++) {
                setItem(i, items.get(i));
            }
        }

        private CompoundTag tryRead(Path file) {
            if (file == null) return null;
            if (!Files.exists(file)) return null;

            try {
                return NbtIo.read(file.toFile());
            } catch (IOException e) {
                System.err.println("[WaystoneInjector] Failed to load Ze Voidrobe NBT: " + e.getMessage());
                return null;
            }
        }

        private void saveToDisk() {
            if (this.file == null) return;

            CompoundTag tag = new CompoundTag();
            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            for (int i = 0; i < SIZE; i++) {
                items.set(i, getItem(i));
            }
            ContainerHelper.saveAllItems(tag, items);

            tryWrite(tag, this.file);
        }

        private boolean tryWrite(CompoundTag tag, Path file) {
            if (file == null) return false;
            try {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                NbtIo.write(tag, file.toFile());
                return true;
            } catch (IOException e) {
                System.err.println("[WaystoneInjector] Failed to save Ze Voidrobe NBT: " + e.getMessage());
                return false;
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
        }

        @Override
        public void stopOpen(@Nonnull Player player) {
            super.stopOpen(player);
            // Save when the menu closes.
            saveToDisk();
        }
    }
}
