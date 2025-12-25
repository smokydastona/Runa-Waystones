package com.example.waystoneinjector.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nonnull;

import java.nio.file.Files;
import java.nio.file.Path;

public class WaystoneInjectorConfigScreen extends Screen {
    private final Screen parent;
    private Path configPath;

    public WaystoneInjectorConfigScreen(Screen parent) {
        super(Component.literal("Waystone Button Injector Config"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve("waystoneinjector-client.toml");

        int centerX = this.width / 2;
        int y = this.height / 4;

        this.addRenderableWidget(Button.builder(Component.literal("Open Config File"), (b) -> openConfigFile())
            .bounds(centerX - 100, y, 200, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Open Config Folder"), (b) -> openConfigFolder())
            .bounds(centerX - 100, y + 24, 200, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Copy Config Path"), (b) -> copyConfigPath())
            .bounds(centerX - 100, y + 48, 200, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), (b) -> onClose())
            .bounds(centerX - 100, y + 84, 200, 20)
            .build());
    }

    private void openConfigFile() {
        Path path = this.configPath;
        if (path != null && Files.exists(path)) {
            Util.getPlatform().openFile(path.toFile());
            return;
        }

        openConfigFolder();
    }

    private void openConfigFolder() {
        Path dir = FMLPaths.CONFIGDIR.get();
        Util.getPlatform().openFile(dir.toFile());
    }

    private void copyConfigPath() {
        Minecraft mc = this.minecraft;
        if (mc == null || this.configPath == null) {
            return;
        }
        mc.keyboardHandler.setClipboard(this.configPath.toString());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    @SuppressWarnings("null")
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int left = 20;
        int y = 55;
        graphics.drawString(this.font, Component.literal("This mod uses a Forge client TOML config."), left, y, 0xA0A0A0);
        y += 12;

        if (this.configPath != null) {
            graphics.drawString(this.font, Component.literal("Config file: " + this.configPath), left, y, 0xA0A0A0);
            y += 12;
        }

        graphics.drawString(this.font, Component.literal("Edit the file externally, then re-open the screen or restart to apply changes."), left, y, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
