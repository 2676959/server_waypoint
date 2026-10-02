package _959.server_waypoint.common.client.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings screen and its theme editor pause singleplayer exactly when the screen that opened
 * them does, so opening them from the pause menu's mod list doesn't let the game run.
 */
class MovementAllowedScreenPauseTest {
    @Test
    void aScreenOpenedFromAPausingScreenPausesToo() {
        assertTrue(MovementAllowedScreen.pausesWith(ParentScreen.create(true)));
    }

    @Test
    void aScreenOpenedFromOneThatLetsTheGameRunLetsItRunToo() {
        assertFalse(MovementAllowedScreen.pausesWith(ParentScreen.create(false)));
    }

    @Test
    void aScreenWithoutAParentLetsTheGameRun() {
        assertFalse(MovementAllowedScreen.pausesWith(null));
    }

    private static final class ParentScreen extends Screen {
        private boolean pauses;

        // Never runs: create() skips constructors, which read the Minecraft instance absent in unit tests.
        private ParentScreen() {
            super(Component.empty());
        }

        private static ParentScreen create(boolean pauses) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                ParentScreen screen = (ParentScreen) unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), ParentScreen.class);
                screen.pauses = pauses;
                return screen;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to create a screen test double", e);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return this.pauses;
        }
    }
}
