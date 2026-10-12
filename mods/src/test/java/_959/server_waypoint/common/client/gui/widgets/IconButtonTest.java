package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetTheme;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconButtonTest {
    @BeforeEach
    void setTestTheme() {
        WidgetThemeManager.setTheme(WidgetTheme.builder(WidgetThemes.CLASSIC)
                .setColor(WidgetThemeVariable.CONTROL_BACKGROUND, 0x81234567)
                .setColor(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND, 0x92345678)
                .setColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, 0xA3456789)
                .build());
    }

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void anIconButtonFillsAtRestUnlessAskedNotTo() {
        assertEquals(0x81234567, button(false).surfaceColor());
        IconButton inactive = button(true);
        inactive.active = false;
        assertEquals(0x81234567, inactive.surfaceColor());
    }

    @Test
    void anIconButtonOnAPanelPaintsNoFillAtRestButKeepsItsHoverFill() {
        assertEquals(0, button(false).withoutRestingFill().surfaceColor());
        assertEquals(0x92345678, button(true).withoutRestingFill().surfaceColor());
        IconButton inactive = button(true).withoutRestingFill();
        inactive.active = false;
        assertEquals(0, inactive.surfaceColor());
    }

    private static IconButton button(boolean hovered) {
        return new IconButton(0, 0, 16, 16, Component.empty(), null, () -> {
        }) {
            {
                this.isHovered = hovered;
            }
        };
    }
}
