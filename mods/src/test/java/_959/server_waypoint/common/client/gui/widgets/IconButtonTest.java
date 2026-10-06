package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemes;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconButtonTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void anIconButtonFillsAtRestUnlessAskedNotTo() {
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);

        assertEquals(0x88000000, button(false).surfaceColor());
        IconButton inactive = button(true);
        inactive.active = false;
        assertEquals(0x88000000, inactive.surfaceColor());
    }

    @Test
    void anIconButtonOnAPanelPaintsNoFillAtRestButKeepsItsHoverFill() {
        WidgetThemeManager.setTheme(WidgetThemes.CLASSIC);

        assertEquals(0, button(false).withoutRestingFill().surfaceColor());
        assertEquals(0x66FFFFFF, button(true).withoutRestingFill().surfaceColor());
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
