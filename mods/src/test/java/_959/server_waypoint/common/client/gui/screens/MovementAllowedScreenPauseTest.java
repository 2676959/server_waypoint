package _959.server_waypoint.common.client.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manager and a pending waypoint edit keep singleplayer running. */
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
    void waypointEditPausesWhileEditing() throws ReflectiveOperationException {
        assertTrue(createEditScreen().isPauseScreen());
    }

    @Test
    void waypointEditRunsSingleplayerUntilTheSaveResultArrives() throws ReflectiveOperationException {
        WaypointEditScreen screen = createEditScreen();
        EditResponseDeadline deadline = editDeadline(screen);

        deadline.begin(7, 0);
        assertFalse(screen.isPauseScreen(), "The integrated server must tick to send the save result");

        assertFalse(deadline.clearIfMatches(6));
        assertFalse(screen.isPauseScreen(), "An unrelated result must not pause a pending save");

        assertTrue(deadline.clearIfMatches(7));
        assertTrue(screen.isPauseScreen(), "Editing pauses again after the matching result");
    }

    @Test
    void waypointEditPausesAgainAfterTheSaveTimesOut() throws ReflectiveOperationException {
        WaypointEditScreen screen = createEditScreen();
        EditResponseDeadline deadline = editDeadline(screen);

        deadline.begin(7, 0);
        assertFalse(screen.isPauseScreen());

        assertTrue(deadline.expire(EditResponseDeadline.TIMEOUT_NANOS));
        assertTrue(screen.isPauseScreen());
    }

    @Test
    void clientConfigPauses() {
        assertTrue(create(ClientConfigScreen.class).isPauseScreen());
    }

    @Test
    void widgetThemeConfigPauses() {
        assertTrue(create(WidgetThemeConfigScreen.class).isPauseScreen());
    }

    private static WaypointEditScreen createEditScreen() throws ReflectiveOperationException {
        WaypointEditScreen screen = create(WaypointEditScreen.class);
        var field = WaypointEditScreen.class.getDeclaredField("responseDeadline");
        field.setAccessible(true);
        field.set(screen, new EditResponseDeadline());
        return screen;
    }

    private static EditResponseDeadline editDeadline(WaypointEditScreen screen) throws ReflectiveOperationException {
        var field = WaypointEditScreen.class.getDeclaredField("responseDeadline");
        field.setAccessible(true);
        return (EditResponseDeadline) field.get(screen);
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
