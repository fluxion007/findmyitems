package dev.smpb.findmyitems.config;

import net.minecraft.client.gui.components.AbstractWidget;
import java.util.List;

/** Typed client-game-test bridge for ConfigScreen's package-private probes. */
public final class ConfigScreenTestAccess {
    private ConfigScreenTestAccess() {
    }

    public static List<AbstractWidget> rows(ConfigScreen screen) {
        return screen.settingsRows();
    }

    public static String rowMessage(ConfigScreen screen, int row) {
        return screen.settingsRows().get(row).getMessage().getString();
    }

    /**
     * Sets a slider row to an exact value. Real mouse dragging has no input-test API; the drag
     * mechanics belong to vanilla's {@code AbstractSliderButton}, while the label text and the
     * config write are this screen's code — both run here.
     */
    public static void setSliderValue(ConfigScreen screen, int row, int value) {
        ((ConfigScreen.IntSlider) screen.settingsRows().get(row)).setIntValue(value);
    }

    /** Screen-space center of a row, for driving real clicks through input. */
    public static double[] rowCenter(ConfigScreen screen, int row) {
        var widget = screen.settingsRows().get(row);
        return new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
    }

    public static java.util.List<int[]> settingsBounds(ConfigScreen screen) {
        return screen.settingsBounds();
    }
}
