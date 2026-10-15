package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The manager paints {@code PANEL_BACKGROUND} over each whole panel, so a widget that sits inside one
 * must not paint it again: a translucent layer composited twice (60% black twice is 84%) makes the
 * widget look darker than the panels beside it.
 */
class EmbeddedPanelBackgroundTest {
    private static final int[] WORLDS = {0xFF000000, 0xFFFFFFFF, 0xFF87B9F0};
    private static final WidgetTheme[] PRESETS = {
            WidgetThemes.TRANSLUCENT_DARK, WidgetThemes.MODERN_DARK, WidgetThemes.HIGH_CONTRAST, WidgetThemes.CLASSIC
    };

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void waypointListLooksLikeThePanelItSitsOn() {
        WaypointListWidget list = new WaypointListWidget(0, 0, 100, 100, null, null, new TestFont(), selection -> {
        });

        assertLooksLikeThePanel(list::backgroundColor);
    }

    @Test
    void serverRailLooksLikeThePanelItSitsOn() {
        ServerListWidget rail = new ServerListWidget(16, 2, server -> {
        });

        assertLooksLikeThePanel(rail::backgroundColor);
    }

    private static void assertLooksLikeThePanel(IntSupplier widgetFill) {
        for (WidgetTheme preset : PRESETS) {
            WidgetThemeManager.setTheme(preset);
            for (int world : WORLDS) {
                int panel = compositeOver(preset.getColor(WidgetThemeVariable.PANEL_BACKGROUND), world);
                int inside = compositeOver(widgetFill.getAsInt(), panel);
                assertEquals(panel, inside, () -> String.format(
                        "over world #%06X the widget pixel #%06X differs from the panel pixel #%06X",
                        world & 0xFFFFFF, inside & 0xFFFFFF, panel & 0xFFFFFF));
            }
        }
    }

    private static int compositeOver(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255.0D;
        int red = compositeChannel(foreground >> 16 & 0xFF, background >> 16 & 0xFF, alpha);
        int green = compositeChannel(foreground >> 8 & 0xFF, background >> 8 & 0xFF, alpha);
        int blue = compositeChannel(foreground & 0xFF, background & 0xFF, alpha);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int compositeChannel(int foreground, int background, double alpha) {
        return (int) Math.round(foreground * alpha + background * (1.0D - alpha));
    }
}
