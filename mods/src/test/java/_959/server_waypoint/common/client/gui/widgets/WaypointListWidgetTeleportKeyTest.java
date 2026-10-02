package _959.server_waypoint.common.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list measures text with the client font and teleports through the live connection, so only
 * its key check runs here. Minecraft 26.3 dispatches SDL codes, where T is not the GLFW code 84.
 */
class WaypointListWidgetTeleportKeyTest {
    @Test
    void tKeyIsTheTeleportShortcut() {
        assertTrue(WaypointListWidget.isTeleportKey(InputConstants.KEY_T));
    }

    @ParameterizedTest
    @ValueSource(ints = {InputConstants.KEY_R, InputConstants.KEY_Y})
    void otherKeysAreNotTheTeleportShortcut(int keyCode) {
        assertFalse(WaypointListWidget.isTeleportKey(keyCode));
    }
}
