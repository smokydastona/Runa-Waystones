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
import net.minecraft.client.gui.components.Tooltip;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public class WaystoneInjectorConfigScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String I18N = "screen.waystoneinjector.config.";

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

    // Special-case: the Nether Portal page wants a dropdown (not a cycling button).
    private DropdownButton<NetherPortalVariant> netherPortalVariantDropdown;

    public enum Page {
        BUTTONS,
        NETHER_PORTAL,
        FEVERDREAM
    }

    public WaystoneInjectorConfigScreen(Screen parent) {
        this(parent, Page.BUTTONS);
    }

    public WaystoneInjectorConfigScreen(Screen parent, Page page) {
        super(Component.translatable(I18N + "title"));
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
        this.addRenderableWidget(Button.builder(text("action.save"), (b) -> onSave())
            .tooltip(Tooltip.create(text("action.save.tooltip")))
            .bounds(this.width / 2 - 154, bottomY, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(text("action.back"), (b) -> onClose())
            .tooltip(Tooltip.create(text("action.back.tooltip")))
            .bounds(this.width / 2 - 50, bottomY, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(text("action.open_file"), (b) -> openConfigFile())
            .tooltip(Tooltip.create(text("action.open_file.tooltip")))
            .bounds(this.width / 2 + 54, bottomY, 100, 20)
            .build());

        buildRows();
        recalcContentHeight();
        updateWidgetPositions();
    }

    private void buildRows() {
        switch (this.page) {
            case BUTTONS -> {
                addHeader(text("page.buttons"));
                for (int i = 1; i <= 6; i++) {
                    addButtonSection(i);
                }
            }
            case NETHER_PORTAL -> {
                addHeader(text("page.nether_portal"));
                this.netherPortalVariantDropdown = addEnumDropdownRow(
                    text("field.variant"),
                    tooltip("field.variant.tooltip"),
                    WaystoneConfig.NETHER_PORTAL_VARIANT.get(),
                    NetherPortalVariant.values(),
                    (val) -> WaystoneConfig.NETHER_PORTAL_VARIANT.set(val)
                );
            }
            case FEVERDREAM -> {
                addHeader(text("page.feverdream"));
                this.feverdreamRedirects = addStringRow(
                    text("field.redirects"),
                    tooltip("field.redirects.tooltip"),
                    String.join(" ; ", WaystoneConfig.FEVERDREAM_REDIRECTS.get()),
                    (value) -> {
                        List<String> parsed = parseRedirectList(value);
                        WaystoneConfig.FEVERDREAM_REDIRECTS.set(parsed);
                    },
                    512
                );

                addIntRow(
                    text("field.death_count"),
                    tooltip("field.death_count.tooltip"),
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
        addCollapsibleHeader(text("section.button", idx), sectionId, tooltip("section.button.tooltip", idx));
        ButtonSpec spec = ButtonSpec.of(idx);

        addBooleanRow(text("field.enabled"), tooltip("field.enabled.tooltip"), spec.enabled.get(), spec.enabled::set);
        addStringRow(text("field.label"), tooltip("field.label.tooltip"), spec.label.get(), spec.label::set, 256);
        addStringRow(text("field.command"), tooltip("field.command.tooltip"), spec.command.get(), spec.command::set, 512);

        addIntRow(text("field.width"), tooltip("field.width.tooltip"), spec.width.get(), 20, 200, spec.width::set);
        addIntRow(text("field.height"), tooltip("field.height.tooltip"), spec.height.get(), 15, 100, spec.height::set);
        addStringRow(text("field.text_color"), tooltip("field.text_color.tooltip"), spec.textColor.get(), spec.textColor::set, 32);

        addEnumStringRow(text("field.side"), tooltip("field.side.tooltip"), spec.side.get(), new String[] { "auto", "left", "right" }, spec.side::set);
        addStringRow(text("field.server_address"), tooltip("field.server_address.tooltip"), spec.serverAddress.get(), spec.serverAddress::set, 256);
        addIntRow(text("field.x_offset"), tooltip("field.x_offset.tooltip"), spec.xOffset.get(), -500, 500, spec.xOffset::set);
        addIntRow(text("field.y_offset"), tooltip("field.y_offset.tooltip"), spec.yOffset.get(), -500, 500, spec.yOffset::set);

        addStringRow(text("field.death_redirect"), tooltip("field.death_redirect.tooltip"), spec.deathRedirect.get(), spec.deathRedirect::set, 512);
        addStringRow(text("field.sleep_redirect"), tooltip("field.sleep_redirect.tooltip"), spec.sleepRedirect.get(), spec.sleepRedirect::set, 512);
        addIntRow(text("field.sleep_chance"), tooltip("field.sleep_chance.tooltip"), spec.sleepChance.get(), 0, 100, spec.sleepChance::set);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(I18N + key, args);
    }

    private static List<Component> tooltip(String key, Object... args) {
        return Arrays.stream(text(key, args).getString().split("\\n"))
            .<Component>map(Component::literal)
            .toList();
    }

    private void addHeader(Component text) {
        rows.add(Row.header(text));
    }

    private void addCollapsibleHeader(Component text, String sectionId, List<Component> tooltip) {
        // Default to expanded for Button 1, collapsed for the rest.
        // This preserves usability while still keeping every option available.
        if (sectionId != null && sectionId.startsWith("button") && !sectionId.equals("button1")) {
            collapsedSections.add(sectionId);
        }
        rows.add(Row.collapsibleHeader(text, sectionId, tooltip));
    }

    private EditBox addStringRow(Component label, List<Component> tooltip, String initial, java.util.function.Consumer<String> apply, int maxLen) {
        EditBox box = new EditBox(this.font, 0, 0, 220, 18, label);
        box.setMaxLength(maxLen);
        box.setValue(initial == null ? "" : initial);
        rows.add(Row.withWidget(label, tooltip, box, () -> apply.accept(box.getValue()), currentSection()));
        this.addRenderableWidget(box);
        return box;
    }

    private void addIntRow(Component label, List<Component> tooltip, int initial, int min, int max, java.util.function.IntConsumer apply) {
        EditBox box = new EditBox(this.font, 0, 0, 100, 18, label);
        box.setMaxLength(16);
        box.setValue(Integer.toString(initial));
        box.setFilter((s) -> s.isEmpty() || s.matches("-?\\d+"));
        rows.add(Row.withWidget(label, tooltip, box, () -> {
            int parsed = parseIntClamped(box.getValue(), initial, min, max);
            apply.accept(parsed);
            box.setValue(Integer.toString(parsed));
        }, currentSection()));
        this.addRenderableWidget(box);
    }

    private void addBooleanRow(Component label, List<Component> tooltip, boolean initial, java.util.function.Consumer<Boolean> apply) {
        ToggleButton toggle = new ToggleButton(0, 0, 100, 20, initial);
        rows.add(Row.withWidget(label, tooltip, toggle, () -> apply.accept(toggle.value()), currentSection()));
        this.addRenderableWidget(toggle);
    }

    private void addEnumStringRow(Component label, List<Component> tooltip, String initial, String[] values, java.util.function.Consumer<String> apply) {
        CycleButton<String> cycle = new CycleButton<>(0, 0, 140, 20, values, initial);
        rows.add(Row.withWidget(label, tooltip, cycle, () -> apply.accept(cycle.value()), currentSection()));
        this.addRenderableWidget(cycle);
    }

    @SuppressWarnings("unused")
    private <T extends Enum<T>> void addEnumRow(String label, T initial, T[] values, java.util.function.Consumer<T> apply) {
        CycleButton<T> cycle = new CycleButton<>(0, 0, 180, 20, values, initial);
        rows.add(Row.withWidget(Component.literal(label), List.of(), cycle, () -> apply.accept(cycle.value()), currentSection()));
        this.addRenderableWidget(cycle);
    }

    private <T> DropdownButton<T> addEnumDropdownRow(Component label, List<Component> tooltip, T initial, T[] values, java.util.function.Consumer<T> apply) {
        DropdownButton<T> dropdown = new DropdownButton<>(0, 0, 180, 20, values, initial);
        rows.add(Row.withWidget(label, tooltip, dropdown, () -> apply.accept(dropdown.value()), currentSection()));
        this.addRenderableWidget(dropdown);
        return dropdown;
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
        if (this.netherPortalVariantDropdown != null && this.netherPortalVariantDropdown.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
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

                Component label = row.label;
                if (row.isSectionHeader && row.collapsible && row.sectionId != null) {
                    boolean collapsed = collapsedSections.contains(row.sectionId);
                    label = Component.literal(collapsed ? "[+] " : "[-] ").append(label);
                }

                graphics.drawString(this.font, label, labelX, y + (row.isHeader ? 4 : 6), color);
            }
            y += row.height;
        }

        if (this.errorMessage != null) {
            graphics.drawCenteredString(this.font, Component.literal(this.errorMessage), this.width / 2, this.height - 52, 0xFF6060);
        }

        // Ensure widgets are positioned for this frame.
        updateWidgetPositions();
        super.render(graphics, mouseX, mouseY, partialTick);

        List<Component> hoveredTooltip = findHoveredRowTooltip(mouseX, mouseY, labelX);
        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            graphics.renderComponentTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private List<Component> findHoveredRowTooltip(int mouseX, int mouseY, int labelX) {
        for (Row row : rows) {
            if (!row.visible || row.tooltip.isEmpty()) {
                continue;
            }
            if (row.isMouseOverLabel(mouseX, mouseY, labelX, this.width - 26) || row.isMouseOverWidget(mouseX, mouseY)) {
                return row.tooltip;
            }
        }
        return null;
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
        final Component label;
        final List<Component> tooltip;
        final boolean isHeader;
        final boolean isSectionHeader;
        final boolean collapsible;
        final String sectionId;
        final int height;
        final List<net.minecraft.client.gui.components.AbstractWidget> widgets;
        final Runnable apply;

        private int y;
        private boolean visible;

        private Row(
            Component label,
            List<Component> tooltip,
            boolean isHeader,
            boolean isSectionHeader,
            boolean collapsible,
            String sectionId,
            int height,
            List<net.minecraft.client.gui.components.AbstractWidget> widgets,
            Runnable apply
        ) {
            this.label = label;
            this.tooltip = tooltip;
            this.isHeader = isHeader;
            this.isSectionHeader = isSectionHeader;
            this.collapsible = collapsible;
            this.sectionId = sectionId;
            this.height = height;
            this.widgets = widgets;
            this.apply = apply;
        }

        static Row header(Component label) {
            return new Row(label, List.of(), true, false, false, null, HEADER_H, List.of(), () -> {});
        }

        static Row collapsibleHeader(Component label, String sectionId, List<Component> tooltip) {
            return new Row(label, tooltip, true, true, true, sectionId, HEADER_H, List.of(), () -> {});
        }

        static Row withWidget(Component label, List<Component> tooltip, net.minecraft.client.gui.components.AbstractWidget widget, Runnable apply, String sectionId) {
            return new Row(label, tooltip, false, false, false, sectionId, ROW_H, List.of(widget), apply);
        }

        void setY(int y) {
            this.y = y;
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
            this.visible = visible;
            for (net.minecraft.client.gui.components.AbstractWidget w : widgets) {
                w.visible = visible;
                w.active = visible;
            }
        }

        boolean isMouseOverLabel(int mouseX, int mouseY, int labelX, int labelRight) {
            return mouseY >= this.y && mouseY < this.y + this.height && mouseX >= labelX && mouseX <= labelRight;
        }

        boolean isMouseOverWidget(int mouseX, int mouseY) {
            for (net.minecraft.client.gui.components.AbstractWidget w : widgets) {
                if (w.visible && w.isMouseOver(mouseX, mouseY)) {
                    return true;
                }
            }
            return false;
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
            return Component.translatable(I18N + (v ? "toggle.on" : "toggle.off"));
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

    private static final class DropdownButton<T> extends net.minecraft.client.gui.components.AbstractWidget {
        private static final int OPTION_H = 20;
        private static final int MAX_VISIBLE_OPTIONS = 8;

        private final T[] values;
        private int index;
        private boolean open;
        private int scrollIndex;

        DropdownButton(int x, int y, int w, int h, T[] values, T initial) {
            super(x, y, w, h, Component.empty());
            this.values = values;
            this.index = 0;
            this.open = false;
            this.scrollIndex = 0;

            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != null && values[i].equals(initial)) {
                        this.index = i;
                        break;
                    }
                }
            }
            updateLabel();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!this.active || !this.visible) {
                return;
            }
            this.open = !this.open;
            clampScroll();
        }

        @Override
        protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Render main "closed" button.
            Button.builder(this.getMessage(), (b) -> {})
                .bounds(this.getX(), this.getY(), this.width, this.height)
                .build()
                .render(graphics, mouseX, mouseY, partialTick);

            if (!this.open) {
                return;
            }

            int listX = this.getX();
            int listY = this.getY() + this.height;
            int visibleCount = Math.min(values.length, MAX_VISIBLE_OPTIONS);
            int listH = visibleCount * OPTION_H;

            // Background panel.
            graphics.fill(listX, listY, listX + this.width, listY + listH, 0xAA000000);

            for (int i = 0; i < visibleCount; i++) {
                int valIndex = this.scrollIndex + i;
                if (valIndex < 0 || valIndex >= values.length) {
                    continue;
                }

                int itemY0 = listY + i * OPTION_H;
                int itemY1 = itemY0 + OPTION_H;

                boolean hovered = mouseX >= listX && mouseX < listX + this.width && mouseY >= itemY0 && mouseY < itemY1;
                boolean selected = valIndex == this.index;
                if (hovered || selected) {
                    graphics.fill(listX, itemY0, listX + this.width, itemY1, hovered ? 0x55333333 : 0x55303030);
                }

                Component text = Component.literal(String.valueOf(values[valIndex]));
                int color = selected ? 0xFFFFFF : 0xE0E0E0;
                int textY = itemY0 + (OPTION_H - 8) / 2;
                graphics.drawCenteredString(Minecraft.getInstance().font, text, listX + this.width / 2, textY, color);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible) {
                return false;
            }

            // Click inside the dropdown list selects an option.
            if (this.open) {
                int listX = this.getX();
                int listY = this.getY() + this.height;
                int visibleCount = Math.min(values.length, MAX_VISIBLE_OPTIONS);
                int listH = visibleCount * OPTION_H;

                boolean inList = mouseX >= listX && mouseX < listX + this.width && mouseY >= listY && mouseY < listY + listH;
                if (inList) {
                    int idxInList = (int) ((mouseY - listY) / OPTION_H);
                    int picked = this.scrollIndex + idxInList;
                    if (picked >= 0 && picked < values.length) {
                        this.index = picked;
                        updateLabel();
                    }
                    this.open = false;
                    return true;
                }
            }

            // Click on main button toggles open/closed.
            if (mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height) {
                onClick(mouseX, mouseY);
                return true;
            }

            // Click outside closes.
            if (this.open) {
                this.open = false;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!this.open || !this.active || !this.visible) {
                return false;
            }

            int listX = this.getX();
            int listY = this.getY() + this.height;
            int visibleCount = Math.min(values.length, MAX_VISIBLE_OPTIONS);
            int listH = visibleCount * OPTION_H;

            boolean inList = mouseX >= listX && mouseX < listX + this.width && mouseY >= listY && mouseY < listY + listH;
            if (!inList) {
                return false;
            }

            int maxScroll = Math.max(0, values.length - visibleCount);
            if (delta > 0) {
                this.scrollIndex = Math.max(0, this.scrollIndex - 1);
            } else if (delta < 0) {
                this.scrollIndex = Math.min(maxScroll, this.scrollIndex + 1);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narration) {
            narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
            if (this.open) {
                narration.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE, Component.literal("Expanded"));
            }
        }

        T value() {
            if (values == null || values.length == 0) {
                return null;
            }
            return values[Math.max(0, Math.min(index, values.length - 1))];
        }

        private void updateLabel() {
            T v = value();
            String text = String.valueOf(v);
            this.setMessage(Component.literal(text + " ▼"));
        }

        private void clampScroll() {
            int visibleCount = Math.min(values.length, MAX_VISIBLE_OPTIONS);
            int maxScroll = Math.max(0, values.length - visibleCount);
            if (this.scrollIndex < 0) this.scrollIndex = 0;
            if (this.scrollIndex > maxScroll) this.scrollIndex = maxScroll;
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
