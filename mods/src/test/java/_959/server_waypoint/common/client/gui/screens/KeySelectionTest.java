package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeySelectionTest {
    @Test
    void aClickSelectsAKey() {
        assertEquals(WidgetThemeVariable.ACCENT,
                KeySelection.click(WidgetThemeVariable.TEXT_PRIMARY, WidgetThemeVariable.ACCENT));
        assertEquals(WidgetThemeVariable.ACCENT, KeySelection.click(null, WidgetThemeVariable.ACCENT));
    }

    @Test
    void aClickOnTheSelectedKeyClearsTheSelection() {
        assertNull(KeySelection.click(WidgetThemeVariable.ACCENT, WidgetThemeVariable.ACCENT));
    }

    @Test
    void upAndDownMoveOneKeyAndStopAtTheEnds() {
        assertEquals(WidgetThemeVariable.TEXT_MUTED, KeySelection.move(WidgetThemeVariable.TEXT_PRIMARY, true));
        assertEquals(WidgetThemeVariable.TEXT_PRIMARY, KeySelection.move(WidgetThemeVariable.TEXT_MUTED, false));
        assertEquals(WidgetThemeVariable.TEXT_PRIMARY, KeySelection.move(WidgetThemeVariable.TEXT_PRIMARY, false));
        assertEquals(WidgetThemeVariable.DANGER_BACKGROUND,
                KeySelection.move(WidgetThemeVariable.DANGER_BACKGROUND, true));
    }

    @Test
    void withNothingSelectedDownStartsAtTheFirstKeyAndUpAtTheLast() {
        assertEquals(WidgetThemeVariable.TEXT_PRIMARY, KeySelection.move(null, true));
        assertEquals(WidgetThemeVariable.DANGER_BACKGROUND, KeySelection.move(null, false));
    }
}
