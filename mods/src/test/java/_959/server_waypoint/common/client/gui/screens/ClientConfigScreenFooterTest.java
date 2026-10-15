package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the footer puts the status message. At a 320-pixel GUI the panel is 300 pixels wide; the
 * English buttons take 165 of them and the Spanish ones 270.
 */
class ClientConfigScreenFooterTest {
    @Test
    void theStatusSitsBesideButtonsThatLeaveRoom() {
        assertFalse(ClientConfigScreen.statusAboveButtons(300, 165));
    }

    @Test
    void theStatusMovesAboveButtonsThatLeaveTooLittleRoom() {
        assertTrue(ClientConfigScreen.statusAboveButtons(300, 270));
    }

    @Test
    void aHundredPixelsBesideTheButtonsIsEnough() {
        // 300 - 192 - 8 = 100
        assertFalse(ClientConfigScreen.statusAboveButtons(300, 192));
        assertTrue(ClientConfigScreen.statusAboveButtons(300, 193));
    }
}
