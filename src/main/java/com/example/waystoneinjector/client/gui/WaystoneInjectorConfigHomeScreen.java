package com.example.waystoneinjector.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Small entry screen so the config isn't one giant scroll menu.
 */
@SuppressWarnings("null")
public class WaystoneInjectorConfigHomeScreen extends Screen {
    private static final String I18N = "screen.waystoneinjector.config.";

    private final Screen parent;

    public WaystoneInjectorConfigHomeScreen(Screen parent) {
        super(Component.translatable(I18N + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 48;
        int w = 200;
        int h = 20;
        int gap = 6;

        this.addRenderableWidget(Button.builder(Component.translatable(I18N + "home.buttons"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.BUTTONS));
        }).tooltip(Tooltip.create(Component.translatable(I18N + "home.buttons.tooltip"))).bounds(centerX - w / 2, y, w, h).build());
        y += h + gap;

        this.addRenderableWidget(Button.builder(Component.translatable(I18N + "home.nether_portal"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.NETHER_PORTAL));
        }).tooltip(Tooltip.create(Component.translatable(I18N + "home.nether_portal.tooltip"))).bounds(centerX - w / 2, y, w, h).build());
        y += h + gap;

        this.addRenderableWidget(Button.builder(Component.translatable(I18N + "home.feverdream"), (b) -> {
            this.minecraft.setScreen(new WaystoneInjectorConfigScreen(this, WaystoneInjectorConfigScreen.Page.FEVERDREAM));
        }).tooltip(Tooltip.create(Component.translatable(I18N + "home.feverdream.tooltip"))).bounds(centerX - w / 2, y, w, h).build());

        int bottomY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.translatable(I18N + "action.done"), (b) -> onClose())
            .tooltip(Tooltip.create(Component.translatable(I18N + "action.done.tooltip")))
            .bounds(centerX - 50, bottomY, 100, 20)
            .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
