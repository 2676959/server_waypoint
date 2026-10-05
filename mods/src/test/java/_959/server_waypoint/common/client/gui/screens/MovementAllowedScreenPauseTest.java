package _959.server_waypoint.common.client.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the waypoint manager keeps singleplayer running while open. */
class MovementAllowedScreenPauseTest {
    @Test
    void waypointManagerDoesNotPause() {
        assertFalse(create(WaypointManagerScreen.class).isPauseScreen());
    }

    @Test
    void waypointAddPauses() {
        assertTrue(create(WaypointAddScreen.class).isPauseScreen());
    }

    @Test
    void waypointEditPauses() {
        assertTrue(create(WaypointEditScreen.class).isPauseScreen());
    }

    @Test
    void clientConfigPauses() {
        assertTrue(create(ClientConfigScreen.class).isPauseScreen());
    }

    @Test
    void widgetThemeConfigPauses() {
        assertTrue(create(WidgetThemeConfigScreen.class).isPauseScreen());
    }

    // Skip constructors, which read the Minecraft instance absent in unit tests.
    private static <T extends Screen> T create(Class<T> screenClass) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return screenClass.cast(unsafeClass
                    .getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), screenClass));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create a screen for pause testing", e);
        }
    }
}
