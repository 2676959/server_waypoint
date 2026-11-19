package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig.TeleportMethod;
import _959.server_waypoint.common.client.ClientWaypointSelection;
import _959.server_waypoint.common.client.ClientWaypointSource;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.WaypointSearchBarWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenShortcutTest {
    @Test
    void textEntrySuppressesManagerShortcuts() {
        assertFalse(WaypointManagerScreen.canUseShortcuts(allocate(EditBox.class)));
        assertFalse(WaypointManagerScreen.canUseShortcuts(allocate(WaypointSearchBarWidget.class)));
        assertFalse(WaypointManagerScreen.canUseShortcuts(allocate(ComboBoxWidget.class)));
    }

    @Test
    void nonTextFocusAllowsManagerShortcuts() {
        assertTrue(WaypointManagerScreen.canUseShortcuts(null));
        assertTrue(WaypointManagerScreen.canUseShortcuts(allocate(IconButton.class)));
    }

    @Test
    void doubleClickRequiresConsecutiveClicksWithinVanillaWindowOnTheSameWaypoint() {
        var waypoint = new ClientWaypointSelection(ClientWaypointSource.SHARED, 1, null,
                "minecraft:overworld", "travel", "home");
        var first = new WaypointManagerScreen.WaypointClick(waypoint, 1, 0);
        var other = waypoint.at(waypoint.dimensionName(), waypoint.listIdentifier(), "village");

        assertFalse(first.completesDoubleClick(null));
        assertTrue(new WaypointManagerScreen.WaypointClick(waypoint, 2, 249_000_000).completesDoubleClick(first));
        assertFalse(new WaypointManagerScreen.WaypointClick(waypoint, 2, 250_000_000).completesDoubleClick(first));
        assertFalse(new WaypointManagerScreen.WaypointClick(waypoint, 3, 1_000_000).completesDoubleClick(first));
        assertFalse(new WaypointManagerScreen.WaypointClick(other, 2, 1_000_000).completesDoubleClick(first));
    }

    @Test
    void singleClickTeleportRequiresTheButtonOfTheSelectedMethod() {
        for (TeleportMethod method : TeleportMethod.values()) {
            assertFalse(WaypointManagerScreen.isSingleClickTeleport(method, InputConstants.MOUSE_BUTTON_LEFT));
            assertEquals(method == TeleportMethod.RIGHT_CLICK,
                    WaypointManagerScreen.isSingleClickTeleport(method, InputConstants.MOUSE_BUTTON_RIGHT));
            assertEquals(method == TeleportMethod.MIDDLE_CLICK,
                    WaypointManagerScreen.isSingleClickTeleport(method, InputConstants.MOUSE_BUTTON_MIDDLE));
        }
    }

    /** Only the listener type matters; skip constructors that need a running Minecraft client. */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to allocate a focus listener", e);
        }
    }
}
