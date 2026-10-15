package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static _959.server_waypoint.common.client.gui.screens.WidgetThemeConfigScreen.panelDraws;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which panels the screen marks for a key, from what they draw at their current size. */
class WidgetThemeConfigScreenMarkerTest {
    private static final List<WidgetThemeVariable> SCROLLBAR_KEYS = List.of(WidgetThemeVariable.SCROLLBAR_TRACK,
            WidgetThemeVariable.SCROLLBAR_THUMB, WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE);

    @Test
    void aPanelIsMarkedForItsScrollbarOnlyWhileItOverflows() {
        for (WidgetThemeVariable key : SCROLLBAR_KEYS) {
            assertTrue(panelDraws(PreviewSample.PREVIEW_PANEL, key, true, false), key.getJsonName());
            assertFalse(panelDraws(PreviewSample.PREVIEW_PANEL, key, false, false), key.getJsonName());
            assertTrue(panelDraws(PreviewSample.KEY_LIST, key, true, true), key.getJsonName());
            assertFalse(panelDraws(PreviewSample.KEY_LIST, key, false, true), key.getJsonName());
        }
    }

    @Test
    void theKeyListIsMarkedForTheValuesColorOnlyWhileItShowsValues() {
        assertTrue(panelDraws(PreviewSample.KEY_LIST, WidgetThemeVariable.TEXT_MUTED, true, true));
        assertFalse(panelDraws(PreviewSample.KEY_LIST, WidgetThemeVariable.TEXT_MUTED, true, false));
    }

    @Test
    void otherKeysFollowWhatThePanelDeclares() {
        assertTrue(panelDraws(PreviewSample.PREVIEW_PANEL, WidgetThemeVariable.DECOR_LINE, false, false));
        assertTrue(panelDraws(PreviewSample.KEY_LIST, WidgetThemeVariable.PANEL_BACKGROUND, false, false));
        assertFalse(panelDraws(PreviewSample.KEY_EDITOR, WidgetThemeVariable.SCROLLBAR_THUMB, true, true));
        assertFalse(panelDraws(PreviewSample.PREVIEW_PANEL, WidgetThemeVariable.TEXT_MUTED, true, true));
    }
}
