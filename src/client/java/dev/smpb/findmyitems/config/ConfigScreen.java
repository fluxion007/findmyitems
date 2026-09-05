package dev.smpb.findmyitems.config;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Vanilla settings screen: one full-width row per setting like Skin Customization, with the
 * standard Done button. Every widget writes straight into the shared config instance, so changes
 * take effect immediately; the file is written on close. Done and Esc both funnel through
 * {@link #onClose}, so neither path can drop an edit.
 */
public final class ConfigScreen extends Screen {
    static final int ROW_WIDTH = 310;
    private static final int TITLE_Y = 20;
    private static final int FIRST_ROW_Y = 52;
    private static final int ROW_SPACING = 24;

    private final Screen parent;
    private final ModConfig config;
    private final Path configPath;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private Button doneButton;

    private ConfigScreen(Screen parent, ModConfig config, Path configPath) {
        super(Component.translatable("screen.findmyitems.config.title"));
        this.parent = parent;
        this.config = config;
        this.configPath = configPath;
    }

    public static Screen create(Screen parent, ModConfig config, Path configPath) {
        return new ConfigScreen(parent, config, configPath);
    }

    @Override
    protected void init() {
        rows.clear();

        // A hand-edited file can hold anything; vanilla clamps options on load, so the widgets
        // always describe values they can actually show.
        config.rescanIntervalSeconds = Math.clamp(config.rescanIntervalSeconds, 0, 30);
        config.searchDistanceChunks = Math.clamp(config.searchDistanceChunks, 0, 32);
        config.retrieveDistanceBlocks = Math.clamp(config.retrieveDistanceBlocks, 0, 256);

        addRow(new IntSlider(Component.translatable("screen.findmyitems.config.rescan_interval"),
                Component.translatable("screen.findmyitems.config.rescan_interval.tooltip"),
                0, 30, config.rescanIntervalSeconds, value -> config.rescanIntervalSeconds = value,
                value -> value == 0 ? "Disabled" : value + "s"));
        addRow(new IntSlider(Component.translatable("screen.findmyitems.config.search_distance"),
                Component.translatable("screen.findmyitems.config.search_distance.tooltip"),
                0, 32, config.searchDistanceChunks, value -> config.searchDistanceChunks = value,
                value -> value == 0 ? "Unlimited" : value == 1 ? "1 chunk" : value + " chunks"));
        addRow(new IntSlider(Component.translatable("screen.findmyitems.config.retrieve_distance"),
                Component.translatable("screen.findmyitems.config.retrieve_distance.tooltip"),
                0, 256, config.retrieveDistanceBlocks, value -> config.retrieveDistanceBlocks = value,
                value -> value == 0 ? "Normal reach" : value + " blocks"));
        addRow(booleanRow(Component.translatable("screen.findmyitems.config.index_ender"),
                Component.translatable("screen.findmyitems.config.index_ender.tooltip"),
                config.indexEnderInventory, value -> config.indexEnderInventory = value));
        addRow(booleanRow(Component.translatable("screen.findmyitems.config.filter_inventory"),
                Component.translatable("screen.findmyitems.config.filter_inventory.tooltip"),
                config.filterInventory, value -> config.filterInventory = value));
        addRow(booleanRow(Component.translatable("screen.findmyitems.config.filter_containers"),
                Component.translatable("screen.findmyitems.config.filter_containers.tooltip"),
                config.filterContainers, value -> config.filterContainers = value));

        doneButton = Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build();
        addRenderableWidget(doneButton);
    }

    private void addRow(AbstractWidget widget) {
        rows.add(widget);
        addRenderableWidget(widget);
        layoutRows();
    }

    private void layoutRows() {
        var y = FIRST_ROW_Y;
        for (var row : rows) {
            row.setX(width / 2 - ROW_WIDTH / 2);
            row.setY(y);
            row.setWidth(ROW_WIDTH);
            y += ROW_SPACING;
        }
    }

    private static CycleButton<Boolean> booleanRow(Component label, Component tooltip, boolean current,
            java.util.function.Consumer<Boolean> save) {
        var button = CycleButton.onOffBuilder(current)
                .create(label, (self, value) -> save.accept(value));
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, TITLE_Y, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        config.save(configPath);
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    /** The rows in display order, for the client game test bridge. */
    List<AbstractWidget> settingsRows() {
        return List.copyOf(rows);
    }

    /** Screen-space bounds of the rows plus Done, for geometry assertions in tests. */
    List<int[]> settingsBounds() {
        var bounds = new ArrayList<int[]>();
        for (var row : rows) {
            bounds.add(new int[] {row.getX(), row.getY(), row.getWidth(), row.getHeight()});
        }
        if (doneButton != null) {
            bounds.add(new int[] {doneButton.getX(), doneButton.getY(), doneButton.getWidth(),
                    doneButton.getHeight()});
        }
        return bounds;
    }

    /** An integer option on a 0–1 slider, labelled the way the old screen labelled it. */
    final class IntSlider extends AbstractSliderButton {
        private final Component label;
        private final int min;
        private final int range;
        private final IntConsumer setter;
        private final java.util.function.IntFunction<String> textGetter;

        IntSlider(Component label, Component tooltip, int min, int max, int initial,
                IntConsumer setter, java.util.function.IntFunction<String> textGetter) {
            // The caller clamps in init, so the initial value always lands on the slider.
            super(0, 0, ROW_WIDTH, 20, Component.empty(), (initial - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.range = max - min;
            this.setter = setter;
            this.textGetter = textGetter;
            setTooltip(Tooltip.create(tooltip));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(label.copy().append(": ").append(textGetter.apply(intValue())));
        }

        @Override
        protected void applyValue() {
            setter.accept(intValue());
        }

        int intValue() {
            return min + (int) Math.round(value * range);
        }

        /** A full drag to an exact value, for tests: real dragging has no input-test API. */
        void setIntValue(int next) {
            value = Math.clamp((next - min) / (double) range, 0.0, 1.0);
            updateMessage();
            applyValue();
        }
    }
}
