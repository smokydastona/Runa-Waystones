package com.example.waystoneinjector.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Small entry screen so the config isn't one giant scroll menu.
 */
@SuppressWarnings("null")
public class WaystoneInjectorConfigHomeScreen extends Screen {

    private final Screen parent;

    public WaystoneInjectorConfigHomeScreen(Screen parent) {
        super(Component.literal("Waystone Button Injector Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 48;
        int w = 200;
        int h = 20;
        int gap = 6;

        this.addRenderableWidget(Button.builder(Component.literal("Buttons"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.BUTTONS));
        }).bounds(centerX - w / 2, y, w, h).build());
        y += h + gap;

        this.addRenderableWidget(Button.builder(Component.literal("Nether Portal"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.NETHER_PORTAL));
        }).bounds(centerX - w / 2, y, w, h).build());
        y += h + gap;

        this.addRenderableWidget(Button.builder(Component.literal("Feverdream"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.FEVERDREAM));
        }).bounds(centerX - w / 2, y, w, h).build());

        int bottomY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), (b) -> onClose())
            .bounds(centerX - 50, bottomY, 100, 20)
            .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
