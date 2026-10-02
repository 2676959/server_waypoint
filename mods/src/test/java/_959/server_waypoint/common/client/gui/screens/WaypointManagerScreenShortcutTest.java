package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.WaypointSearchBarWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Test;

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
