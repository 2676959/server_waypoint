package _959.server_waypoint.common.client.gui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IconControlRendererTest {
    @Test
    void focusedClosedDropdownUsesTheNormalBorder() {
        assertEquals(
                WidgetThemeVariable.BORDER,
                IconControlRenderer.resolveBorder(true, true, false, false)
        );
    }

    @Test
    void expandedFocusAndHoverStillUseTheFocusRing() {
        assertEquals(
                WidgetThemeVariable.FOCUS_RING,
                IconControlRenderer.resolveBorder(true, true, false, true)
        );
        assertEquals(
                WidgetThemeVariable.FOCUS_RING,
                IconControlRenderer.resolveBorder(true, false, true, false)
        );
    }

    @Test
    void railControlsPaintNoFillAtRestButKeepTheirStateFills() {
        // The panel under a rail control already paints a translucent fill, so a second fill at rest
        // would stack on it. Hover and selection keep their fills; disabled icons use a tint instead.
        assertNull(IconControlRenderer.resolveFill(true, false, false, false));
        assertEquals(
                WidgetThemeVariable.CONTROL_HOVER_BACKGROUND,
                IconControlRenderer.resolveFill(true, false, true, false)
        );
        assertEquals(
                WidgetThemeVariable.SELECTION_BACKGROUND,
                IconControlRenderer.resolveFill(true, true, false, false)
        );
        assertNull(IconControlRenderer.resolveFill(false, false, false, false));
    }

    @Test
    void popupRowsKeepTheirRestingFillBecauseTheyFloatOverOtherWidgets() {
        assertEquals(
                WidgetThemeVariable.CONTROL_BACKGROUND,
                IconControlRenderer.resolveFill(true, false, false, true)
        );
    }

    @Test
    void inactiveControlAlwaysUsesTheNormalBorder() {
        assertEquals(
                WidgetThemeVariable.BORDER,
                IconControlRenderer.resolveBorder(false, true, true, true)
        );
    }
}
