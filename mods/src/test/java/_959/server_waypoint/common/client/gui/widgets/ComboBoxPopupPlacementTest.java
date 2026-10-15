package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboBoxPopupPlacementTest {
    @Test
    void opensDownWhenTheMenuFitsEvenWithMoreRoomAbove() {
        assertFalse(ComboBoxWidget.shouldOpenUp(300, 11, 88, 470));
    }

    @Test
    void exactFitBelowKeepsTheMenuDown() {
        assertFalse(ComboBoxWidget.shouldOpenUp(300, 11, 88, 403));
    }

    @Test
    void shorterSuggestionsCanOpenDownWhenTheFullDropdownOpensUp() {
        assertTrue(ComboBoxWidget.shouldOpenUp(300, 11, 88, 380));
        assertFalse(ComboBoxWidget.shouldOpenUp(300, 11, 55, 380));
    }

    @Test
    void nearTheBottomOpensUp() {
        assertTrue(ComboBoxWidget.shouldOpenUp(450, 11, 88, 470));
    }

    @Test
    void neitherSideFitsUsesTheLargerSideAndTiesPreferDown() {
        assertTrue(ComboBoxWidget.shouldOpenUp(60, 11, 88, 110));
        assertFalse(ComboBoxWidget.shouldOpenUp(40, 11, 88, 110));
        assertFalse(ComboBoxWidget.shouldOpenUp(50, 10, 88, 110));
    }
}
