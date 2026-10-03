package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The search field sits on the manager's list panel, which already paints a translucent fill, so at
 * rest it paints none of its own: a second fill would stack on the panel.
 */
class WaypointSearchBarWidgetTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void searchFieldPaintsNoFillAtRest() {
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);

        assertEquals(0, searchBar(false, true).surfaceColor());
    }

    @Test
    void searchFieldKeepsItsHoverAndDisabledFills() {
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);

        assertEquals(0x66FFFFFF, searchBar(true, true).surfaceColor());
        assertEquals(0x55000000, searchBar(false, false).surfaceColor());
    }

    @Test
    void otherTextFieldsStillFillAtRest() {
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);

        TranslucentTextField field = new TranslucentTextField(0, 0, 60, Component.empty(), new TestFont());

        assertEquals(0x88000000, field.surfaceColor());
    }

    @Test
    void searchHintTakesThePlaceholderColorOfTheTheme() {
        // With no fill of its own the field's hint sits on the bare panel, where vanilla's fixed dark gray
        // hint (#555555) is 1.2:1 against a light world. The themed placeholder stays readable.
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);
        List<Component> hints = new ArrayList<>();

        new WaypointSearchBarWidget(0, 0, 100, Component.empty(), new TestFont(), query -> {
        }) {
            @Override
            public void setHint(Component hint) {
                hints.add(hint);
                super.setHint(hint);
            }
        };

        assertFalse(hints.isEmpty(), "the search field installs a themed hint");
        assertEquals(0xA0A0A0, hints.get(hints.size() - 1).getStyle().getColor().getValue());
    }

    private static WaypointSearchBarWidget searchBar(boolean hovered, boolean active) {
        WaypointSearchBarWidget bar = new WaypointSearchBarWidget(
                0, 0, 100, Component.empty(), new TestFont(), query -> {
                }) {
            {
                this.isHovered = hovered;
            }
        };
        bar.active = active;
        return bar;
    }
}
