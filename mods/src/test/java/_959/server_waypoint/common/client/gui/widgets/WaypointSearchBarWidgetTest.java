package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The search field sits on the manager's list panel, which already paints a translucent fill, so at
 * rest it paints none of its own: a second fill would stack on the panel.
 */
class WaypointSearchBarWidgetTest {
    @BeforeEach
    void setTestTheme() {
        WidgetThemeManager.setTheme(WidgetTheme.builder(WidgetThemes.CLASSIC)
                .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0x81234567)
                .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x92345678)
                .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xA3456789)
                .setColor(WidgetThemeVariable.TEXT_PLACEHOLDER, 0xFF56789A)
                .build());
    }

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void searchFieldPaintsNoFillAtRest() {
        assertEquals(0, searchBar(false, true).surfaceColor());
    }

    @Test
    void searchFieldKeepsItsHoverAndDisabledFills() {
        assertEquals(0x92345678, searchBar(true, true).surfaceColor());
        assertEquals(0xA3456789, searchBar(false, false).surfaceColor());
    }

    @Test
    void otherTextFieldsStillFillAtRest() {
        TranslucentTextField field = new TranslucentTextField(0, 0, 60, Component.empty(), new TestFont());

        assertEquals(0x81234567, field.surfaceColor());
    }

    @Test
    void searchHintTakesThePlaceholderColorOfTheTheme() {
        // With no fill of its own the field's hint sits on the bare panel, so it must use the theme's
        // placeholder color instead of vanilla's fixed dark gray hint (#555555).
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
        assertEquals(0x56789A, hints.get(hints.size() - 1).getStyle().getColor().getValue());
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
