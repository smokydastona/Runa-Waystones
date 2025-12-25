package com.example.waystoneinjector.singleplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

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
public final class SingleplayerVoidCloset {

    public static final int SIZE = 54;

    private SingleplayerVoidCloset() {
    }

    public static void open(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;

            Path file = getVaultFile(server, playerId);
            PersistentContainer container = new PersistentContainer(file);

            MenuProvider provider = new SimpleMenuProvider(
                (containerId, inv, p) -> ChestMenu.sixRows(containerId, inv, container),
                Component.literal("Ze Voidrobe")
            );

            player.openMenu(provider);
        });
    }

    private static Path getVaultFile(MinecraftServer server, UUID playerId) {
        // Prefer per-world storage.
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path dir = worldRoot.resolve("data").resolve("waystoneinjector").resolve("void_closet");
            Files.createDirectories(dir);
            return dir.resolve(playerId.toString() + ".dat");
        } catch (Throwable t) {
            // Fallback: config folder (still local, but shared across worlds)
            try {
                Path dir = Path.of("config", "waystoneinjector", "void_closet");
                Files.createDirectories(dir);
                return dir.resolve(playerId.toString() + ".dat");
            } catch (Throwable ignored) {
                return Path.of("waystoneinjector_void_closet_" + playerId + ".dat");
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
            if (file == null) return;
            if (!Files.exists(file)) return;

            try {
                CompoundTag tag = NbtIo.read(file.toFile());
                if (tag == null) return;

                NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(tag, items);
                for (int i = 0; i < SIZE; i++) {
                    setItem(i, items.get(i));
                }
            } catch (IOException e) {
                System.err.println("[WaystoneInjector] Failed to load Ze Voidrobe NBT: " + e.getMessage());
            }
        }

        private void saveToDisk() {
            if (file == null) return;
            try {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }

                NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
                for (int i = 0; i < SIZE; i++) {
                    items.set(i, getItem(i));
                }

                CompoundTag tag = new CompoundTag();
                ContainerHelper.saveAllItems(tag, items);
                NbtIo.write(tag, file.toFile());
            } catch (IOException e) {
                System.err.println("[WaystoneInjector] Failed to save Ze Voidrobe NBT: " + e.getMessage());
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
