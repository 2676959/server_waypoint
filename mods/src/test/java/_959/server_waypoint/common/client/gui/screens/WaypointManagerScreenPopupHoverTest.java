package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointManagerScreenPopupHoverTest {
    @Test
    void controlsBelowAPopupUnderThePointerSeeNoPointer() {
        // The popup can open over the sort-order toggle. The toggle is drawn first, so if it still saw the
        // pointer it would be hovered under the popup item and its tooltip would hide the item's.
        assertEquals(NO_MOUSE, WaypointManagerScreen.resolveMouseBeneathPopup(57, true));
        assertEquals(NO_MOUSE, WaypointManagerScreen.resolveMouseBeneathPopup(0, true));
    }

    @Test
    void controlsKeepThePointerWhenNoPopupIsUnderIt() {
        // An open popup elsewhere, or none at all, must not switch off hover for the rest of the screen.
        assertEquals(57, WaypointManagerScreen.resolveMouseBeneathPopup(57, false));
        assertEquals(0, WaypointManagerScreen.resolveMouseBeneathPopup(0, false));
    }
}
