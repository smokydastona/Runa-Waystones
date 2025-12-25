package com.example.waystoneinjector.client.gui;

import com.example.waystoneinjector.WaystoneInjectorMod;
import com.example.waystoneinjector.config.NetherPortalVariant;
import com.example.waystoneinjector.config.WaystoneConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public class WaystoneInjectorConfigScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int TOP = 32;
    private static final int BOTTOM_PADDING = 36;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 18;

    private final Screen parent;
    private final Page page;
    private final List<Row> rows = new ArrayList<>();

    // Collapsible sections (used to keep the per-button settings from becoming a wall of fields).
    private final Set<String> collapsedSections = new HashSet<>();

    private double scroll;
    private int contentHeight;
    private String errorMessage;

    private Path configPath;

    // Special-case: feverdream redirects are a list in TOML.
    private EditBox feverdreamRedirects;

    public enum Page {
        BUTTONS,
        NETHER_PORTAL,
        FEVERDREAM
    }

    public WaystoneInjectorConfigScreen(Screen parent) {
        this(parent, Page.BUTTONS);
    }

    public WaystoneInjectorConfigScreen(Screen parent, Page page) {
        super(Component.literal("Waystone Button Injector Config"));
        this.parent = parent;
        this.page = page == null ? Page.BUTTONS : page;
    }

    @Override
    protected void init() {
        this.errorMessage = null;
        this.rows.clear();
        this.collapsedSections.clear();
        this.scroll = clampScroll(this.scroll);
        this.configPath = FMLPaths.CONFIGDIR.get().resolve("waystoneinjector-client.toml");

        int bottomY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), (b) -> onSave())
            .bounds(this.width / 2 - 154, bottomY, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), (b) -> onClose())
            .bounds(this.width / 2 - 50, bottomY, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Open File"), (b) -> openConfigFile())
            .bounds(this.width / 2 + 54, bottomY, 100, 20)
            .build());

        buildRows();
        recalcContentHeight();
        updateWidgetPositions();
    }

    private void buildRows() {
        switch (this.page) {
            case BUTTONS -> {
                addHeader("Buttons");
                for (int i = 1; i <= 6; i++) {
                    addButtonSection(i);
                }
            }
            case NETHER_PORTAL -> {
                addHeader("Nether Portal");
                addEnumRow(
                    "Variant",
                    WaystoneConfig.NETHER_PORTAL_VARIANT.get(),
                    NetherPortalVariant.values(),
                    (val) -> WaystoneConfig.NETHER_PORTAL_VARIANT.set(val)
                );
            }
            case FEVERDREAM -> {
                addHeader("Feverdream");
                this.feverdreamRedirects = addStringRow(
                    "Redirects",
                    String.join(" ; ", WaystoneConfig.FEVERDREAM_REDIRECTS.get()),
                    (value) -> {
                        List<String> parsed = parseRedirectList(value);
                        WaystoneConfig.FEVERDREAM_REDIRECTS.set(parsed);
                    },
                    512
                );

                addIntRow(
                    "Death Count",
                    WaystoneConfig.FEVERDREAM_DEATH_COUNT.get(),
                    1,
                    10,
                    (val) -> WaystoneConfig.FEVERDREAM_DEATH_COUNT.set(val)
                );
            }
        }
    }

    private void addButtonSection(int idx) {
        String sectionId = "button" + idx;
        addCollapsibleHeader("Button " + idx, sectionId);
        ButtonSpec spec = ButtonSpec.of(idx);

        addBooleanRow("Enabled", spec.enabled.get(), spec.enabled::set);
        addStringRow("Label", spec.label.get(), spec.label::set, 256);
        addStringRow("Command", spec.command.get(), spec.command::set, 512);

        addIntRow("Width", spec.width.get(), 20, 200, spec.width::set);
        addIntRow("Height", spec.height.get(), 15, 100, spec.height::set);
        addStringRow("Text Color", spec.textColor.get(), spec.textColor::set, 32);

        addEnumStringRow("Side", spec.side.get(), new String[] { "auto", "left", "right" }, spec.side::set);
        addStringRow("Server Address", spec.serverAddress.get(), spec.serverAddress::set, 256);
        addIntRow("X Offset", spec.xOffset.get(), -500, 500, spec.xOffset::set);
        addIntRow("Y Offset", spec.yOffset.get(), -500, 500, spec.yOffset::set);

        addStringRow("Death Redirect", spec.deathRedirect.get(), spec.deathRedirect::set, 512);
        addStringRow("Sleep Redirect", spec.sleepRedirect.get(), spec.sleepRedirect::set, 512);
        addIntRow("Sleep Chance", spec.sleepChance.get(), 0, 100, spec.sleepChance::set);
    }

    private void addHeader(String text) {
        rows.add(Row.header(text));
    }

    private void addCollapsibleHeader(String text, String sectionId) {
        // Default to expanded for Button 1, collapsed for the rest.
        // This preserves usability while still keeping every option available.
        if (sectionId != null && sectionId.startsWith("button") && !sectionId.equals("button1")) {
            collapsedSections.add(sectionId);
        }
        rows.add(Row.collapsibleHeader(text, sectionId));
    }

    private EditBox addStringRow(String label, String initial, java.util.function.Consumer<String> apply, int maxLen) {
        EditBox box = new EditBox(this.font, 0, 0, 220, 18, Component.literal(label));
        box.setMaxLength(maxLen);
        box.setValue(initial == null ? "" : initial);
        rows.add(Row.withWidget(label, box, () -> apply.accept(box.getValue()), currentSection()));
        this.addRenderableWidget(box);
        return box;
    }

    private void addIntRow(String label, int initial, int min, int max, java.util.function.IntConsumer apply) {
        EditBox box = new EditBox(this.font, 0, 0, 100, 18, Component.literal(label));
        box.setMaxLength(16);
        box.setValue(Integer.toString(initial));
        box.setFilter((s) -> s.isEmpty() || s.matches("-?\\d+"));
        rows.add(Row.withWidget(label, box, () -> {
            int parsed = parseIntClamped(box.getValue(), initial, min, max);
            apply.accept(parsed);
            box.setValue(Integer.toString(parsed));
        }, currentSection()));
        this.addRenderableWidget(box);
    }

    private void addBooleanRow(String label, boolean initial, java.util.function.Consumer<Boolean> apply) {
        ToggleButton toggle = new ToggleButton(0, 0, 100, 20, initial);
        rows.add(Row.withWidget(label, toggle, () -> apply.accept(toggle.value()), currentSection()));
        this.addRenderableWidget(toggle);
    }

    private void addEnumStringRow(String label, String initial, String[] values, java.util.function.Consumer<String> apply) {
        CycleButton<String> cycle = new CycleButton<>(0, 0, 140, 20, values, initial);
        rows.add(Row.withWidget(label, cycle, () -> apply.accept(cycle.value()), currentSection()));
        this.addRenderableWidget(cycle);
    }

    private <T extends Enum<T>> void addEnumRow(String label, T initial, T[] values, java.util.function.Consumer<T> apply) {
        CycleButton<T> cycle = new CycleButton<>(0, 0, 180, 20, values, initial);
        rows.add(Row.withWidget(label, cycle, () -> apply.accept(cycle.value()), currentSection()));
        this.addRenderableWidget(cycle);
    }

    private String currentSection() {
        // Most-recent collapsible header is the active section.
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if (r.isSectionHeader && r.sectionId != null) {
                return r.sectionId;
            }
        }
        return null;
    }

    private void onSave() {
        this.errorMessage = null;
        try {
            if (this.feverdreamRedirects != null) {
                List<String> parsed = parseRedirectList(this.feverdreamRedirects.getValue());
                for (String entry : parsed) {
                    if (!isValidFeverdreamRedirect(entry)) {
                        this.errorMessage = "Invalid Feverdream redirect: " + entry;
                        return;
                    }
                }
            }

            for (Row row : rows) {
                row.apply();
            }

            ModConfig clientCfg = findClientConfig();
            if (clientCfg != null) {
                clientCfg.save();
                LOGGER.info("Saved client config to {}", clientCfg.getFullPath());
            } else {
                LOGGER.warn("Could not find ModConfig instance; values set in memory but not saved");
            }

            onClose();
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
            this.errorMessage = "Failed to save config: " + e.getClass().getSimpleName();
        }
    }

    private static ModConfig findClientConfig() {
        try {
            Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.CLIENT);
            if (configs == null) {
                return null;
            }
            for (ModConfig cfg : configs) {
                if (WaystoneInjectorMod.MODID.equals(cfg.getModId())) {
                    return cfg;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openConfigFile() {
        Path path = this.configPath;
        if (path != null && Files.exists(path)) {
            Util.getPlatform().openFile(path.toFile());
            return;
        }
        Util.getPlatform().openFile(FMLPaths.CONFIGDIR.get().toFile());
    }

    @Override
    public void onClose() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(this.parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewBottom = this.height - BOTTOM_PADDING;
        if (mouseY < TOP || mouseY > viewBottom) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        this.scroll = clampScroll(this.scroll - delta * 18.0);
        updateWidgetPositions();
        return true;
    }

    private double clampScroll(double value) {
        int viewHeight = (this.height - BOTTOM_PADDING) - TOP;
        int max = Math.max(0, this.contentHeight - viewHeight);
        if (value < 0) return 0;
        if (value > max) return max;
        return value;
    }

    private void recalcContentHeight() {
        int h = 0;
        for (Row row : rows) {
            if (row.isHeader) {
                h += row.height;
                continue;
            }
            if (row.sectionId != null && collapsedSections.contains(row.sectionId)) {
                continue;
            }
            h += row.height;
        }
        this.contentHeight = h;
        this.scroll = clampScroll(this.scroll);
    }

    private void updateWidgetPositions() {
        int viewBottom = this.height - BOTTOM_PADDING;
        int y = TOP - (int) this.scroll;
        for (Row row : rows) {
            row.setY(y);

            boolean layoutVisible = row.isHeader || row.sectionId == null || !collapsedSections.contains(row.sectionId);
            if (!layoutVisible) {
                row.setVisible(false);
                continue;
            }

            boolean visible = y + row.height >= TOP && y <= viewBottom;
            row.setVisible(visible);
            y += row.height;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Allow clicking on per-button headers to collapse/expand.
        int viewBottom = this.height - BOTTOM_PADDING;
        if (mouseY >= TOP && mouseY <= viewBottom) {
            int y = TOP - (int) this.scroll;
            for (Row row : rows) {
                boolean layoutVisible = row.isHeader || row.sectionId == null || !collapsedSections.contains(row.sectionId);

                if (row.isSectionHeader && row.collapsible && layoutVisible) {
                    if (mouseY >= y && mouseY <= y + row.height) {
                        toggleSection(row.sectionId);
                        return true;
                    }
                }

                if (!layoutVisible) {
                    continue;
                }
                y += row.height;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleSection(String sectionId) {
        if (sectionId == null) return;
        if (collapsedSections.contains(sectionId)) {
            collapsedSections.remove(sectionId);
        } else {
            collapsedSections.add(sectionId);
        }
        recalcContentHeight();
        updateWidgetPositions();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Clip-like behavior: we just avoid drawing labels outside the scroll area.
        int viewBottom = this.height - BOTTOM_PADDING;
        int labelX = 18;

        int y = TOP - (int) this.scroll;
        for (Row row : rows) {
            boolean layoutVisible = row.isHeader || row.sectionId == null || !collapsedSections.contains(row.sectionId);
            if (!layoutVisible) {
                continue;
            }

            if (y + row.height >= TOP && y <= viewBottom) {
                int color = row.isHeader ? 0xFFD080 : 0xE0E0E0;

                String label = row.label;
                if (row.isSectionHeader && row.collapsible && row.sectionId != null) {
                    boolean collapsed = collapsedSections.contains(row.sectionId);
                    label = (collapsed ? "[+] " : "[-] ") + label;
                }

                graphics.drawString(this.font, Component.literal(label), labelX, y + (row.isHeader ? 4 : 6), color);
            }
            y += row.height;
        }

        if (this.errorMessage != null) {
            graphics.drawCenteredString(this.font, Component.literal(this.errorMessage), this.width / 2, this.height - 52, 0xFF6060);
        }

        // Ensure widgets are positioned for this frame.
        updateWidgetPositions();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static int parseIntClamped(String value, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min) return min;
            if (parsed > max) return max;
            return parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> parseRedirectList(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static boolean isValidFeverdreamRedirect(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        return trimmed.contains("->") && (trimmed.startsWith("death:") || trimmed.startsWith("sleep:"));
    }

    private static final class Row {
        final String label;
        final boolean isHeader;
        final boolean isSectionHeader;
        final boolean collapsible;
        final String sectionId;
        final int height;
        final List<net.minecraft.client.gui.components.AbstractWidget> widgets;
        final Runnable apply;

        private Row(
            String label,
            boolean isHeader,
            boolean isSectionHeader,
            boolean collapsible,
            String sectionId,
            int height,
            List<net.minecraft.client.gui.components.AbstractWidget> widgets,
            Runnable apply
        ) {
            this.label = label;
            this.isHeader = isHeader;
            this.isSectionHeader = isSectionHeader;
            this.collapsible = collapsible;
            this.sectionId = sectionId;
            this.height = height;
            this.widgets = widgets;
            this.apply = apply;
        }

        static Row header(String label) {
            return new Row(label, true, false, false, null, HEADER_H, List.of(), () -> {});
        }

        static Row collapsibleHeader(String label, String sectionId) {
            return new Row(label, true, true, true, sectionId, HEADER_H, List.of(), () -> {});
        }

        static Row withWidget(String label, net.minecraft.client.gui.components.AbstractWidget widget, Runnable apply, String sectionId) {
            return new Row(label, false, false, false, sectionId, ROW_H, List.of(widget), apply);
        }

        void setY(int y) {
            int widgetX = 0;
            for (net.minecraft.client.gui.components.AbstractWidget w : widgets) {
                widgetX = w.getWidth() == 100 ?  thisWidgetX(100) : thisWidgetX(w.getWidth());
                w.setX(widgetX);
                w.setY(y + 2);
            }
        }

        private int thisWidgetX(int widgetWidth) {
            // Right-align widgets with a small margin.
            return Minecraft.getInstance().getWindow().getGuiScaledWidth() - widgetWidth - 18;
        }

        void setVisible(boolean visible) {
            for (net.minecraft.client.gui.components.AbstractWidget w : widgets) {
                w.visible = visible;
                w.active = visible;
            }
        }

        void apply() {
            if (apply != null) {
                apply.run();
            }
        }
    }

    private static final class ToggleButton extends Button {
        private boolean value;

        ToggleButton(int x, int y, int w, int h, boolean initial) {
            super(x, y, w, h, labelFor(initial), (b) -> {}, DEFAULT_NARRATION);
            this.value = initial;
        }

        @Override
        public void onPress() {
            this.value = !this.value;
            this.setMessage(labelFor(this.value));
        }

        boolean value() {
            return this.value;
        }

        private static Component labelFor(boolean v) {
            return Component.literal(v ? "ON" : "OFF");
        }
    }

    private static final class CycleButton<T> extends Button {
        private final T[] values;
        private int index;

        CycleButton(int x, int y, int w, int h, T[] values, T initial) {
            super(x, y, w, h, Component.literal(String.valueOf(initial)), (b) -> {}, DEFAULT_NARRATION);
            this.values = values;
            this.index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(initial)) {
                    this.index = i;
                    break;
                }
            }
            this.setMessage(Component.literal(String.valueOf(values[this.index])));
        }

        @Override
        public void onPress() {
            if (values.length == 0) {
                return;
            }
            this.index = (this.index + 1) % values.length;
            this.setMessage(Component.literal(String.valueOf(values[this.index])));
        }

        T value() {
            if (values.length == 0) {
                return null;
            }
            return values[this.index];
        }
    }

    private record ButtonSpec(
        net.minecraftforge.common.ForgeConfigSpec.BooleanValue enabled,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> label,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> command,
        net.minecraftforge.common.ForgeConfigSpec.IntValue width,
        net.minecraftforge.common.ForgeConfigSpec.IntValue height,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> textColor,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> side,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> serverAddress,
        net.minecraftforge.common.ForgeConfigSpec.IntValue xOffset,
        net.minecraftforge.common.ForgeConfigSpec.IntValue yOffset,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> deathRedirect,
        net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> sleepRedirect,
        net.minecraftforge.common.ForgeConfigSpec.IntValue sleepChance
    ) {
        static ButtonSpec of(int idx) {
            return switch (idx) {
                case 1 -> new ButtonSpec(
                    WaystoneConfig.BUTTON1_ENABLED,
                    WaystoneConfig.BUTTON1_LABEL,
                    WaystoneConfig.BUTTON1_COMMAND,
                    WaystoneConfig.BUTTON1_WIDTH,
                    WaystoneConfig.BUTTON1_HEIGHT,
                    WaystoneConfig.BUTTON1_TEXT_COLOR,
                    WaystoneConfig.BUTTON1_SIDE,
                    WaystoneConfig.BUTTON1_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON1_X_OFFSET,
                    WaystoneConfig.BUTTON1_Y_OFFSET,
                    WaystoneConfig.BUTTON1_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON1_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON1_SLEEP_CHANCE
                );
                case 2 -> new ButtonSpec(
                    WaystoneConfig.BUTTON2_ENABLED,
                    WaystoneConfig.BUTTON2_LABEL,
                    WaystoneConfig.BUTTON2_COMMAND,
                    WaystoneConfig.BUTTON2_WIDTH,
                    WaystoneConfig.BUTTON2_HEIGHT,
                    WaystoneConfig.BUTTON2_TEXT_COLOR,
                    WaystoneConfig.BUTTON2_SIDE,
                    WaystoneConfig.BUTTON2_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON2_X_OFFSET,
                    WaystoneConfig.BUTTON2_Y_OFFSET,
                    WaystoneConfig.BUTTON2_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON2_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON2_SLEEP_CHANCE
                );
                case 3 -> new ButtonSpec(
                    WaystoneConfig.BUTTON3_ENABLED,
                    WaystoneConfig.BUTTON3_LABEL,
                    WaystoneConfig.BUTTON3_COMMAND,
                    WaystoneConfig.BUTTON3_WIDTH,
                    WaystoneConfig.BUTTON3_HEIGHT,
                    WaystoneConfig.BUTTON3_TEXT_COLOR,
                    WaystoneConfig.BUTTON3_SIDE,
                    WaystoneConfig.BUTTON3_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON3_X_OFFSET,
                    WaystoneConfig.BUTTON3_Y_OFFSET,
                    WaystoneConfig.BUTTON3_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON3_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON3_SLEEP_CHANCE
                );
                case 4 -> new ButtonSpec(
                    WaystoneConfig.BUTTON4_ENABLED,
                    WaystoneConfig.BUTTON4_LABEL,
                    WaystoneConfig.BUTTON4_COMMAND,
                    WaystoneConfig.BUTTON4_WIDTH,
                    WaystoneConfig.BUTTON4_HEIGHT,
                    WaystoneConfig.BUTTON4_TEXT_COLOR,
                    WaystoneConfig.BUTTON4_SIDE,
                    WaystoneConfig.BUTTON4_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON4_X_OFFSET,
                    WaystoneConfig.BUTTON4_Y_OFFSET,
                    WaystoneConfig.BUTTON4_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON4_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON4_SLEEP_CHANCE
                );
                case 5 -> new ButtonSpec(
                    WaystoneConfig.BUTTON5_ENABLED,
                    WaystoneConfig.BUTTON5_LABEL,
                    WaystoneConfig.BUTTON5_COMMAND,
                    WaystoneConfig.BUTTON5_WIDTH,
                    WaystoneConfig.BUTTON5_HEIGHT,
                    WaystoneConfig.BUTTON5_TEXT_COLOR,
                    WaystoneConfig.BUTTON5_SIDE,
                    WaystoneConfig.BUTTON5_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON5_X_OFFSET,
                    WaystoneConfig.BUTTON5_Y_OFFSET,
                    WaystoneConfig.BUTTON5_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON5_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON5_SLEEP_CHANCE
                );
                case 6 -> new ButtonSpec(
                    WaystoneConfig.BUTTON6_ENABLED,
                    WaystoneConfig.BUTTON6_LABEL,
                    WaystoneConfig.BUTTON6_COMMAND,
                    WaystoneConfig.BUTTON6_WIDTH,
                    WaystoneConfig.BUTTON6_HEIGHT,
                    WaystoneConfig.BUTTON6_TEXT_COLOR,
                    WaystoneConfig.BUTTON6_SIDE,
                    WaystoneConfig.BUTTON6_SERVER_ADDRESS,
                    WaystoneConfig.BUTTON6_X_OFFSET,
                    WaystoneConfig.BUTTON6_Y_OFFSET,
                    WaystoneConfig.BUTTON6_DEATH_REDIRECT,
                    WaystoneConfig.BUTTON6_SLEEP_REDIRECT,
                    WaystoneConfig.BUTTON6_SLEEP_CHANCE
                );
                default -> throw new IllegalArgumentException("Invalid button index: " + idx);
            };
        }
    }
}
