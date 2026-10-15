//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.widgets.IconButton;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAllowedScreenButtonKeyTest {
    @Test
    void keyboardFocusedButtonTakesSpaceInsteadOfJumping() {
        TestScreen screen = TestScreen.create(true);
        AtomicInteger presses = new AtomicInteger();
        focusButton(screen, presses);

        assertTrue(screen.keyPressed(InputConstants.KEY_SPACE, 0, 0));
        assertEquals(1, presses.get());
        assertEquals(List.of(), screen.movementKeys);
    }

    @Test
    void mouseFocusedButtonLeavesTheJumpKeyToMovement() {
        TestScreen screen = TestScreen.create(false);
        AtomicInteger presses = new AtomicInteger();
        focusButton(screen, presses);

        assertTrue(screen.keyPressed(InputConstants.KEY_SPACE, 0, 0));
        assertEquals(0, presses.get());
        assertEquals(List.of(InputConstants.KEY_SPACE), screen.movementKeys);
    }

    @ParameterizedTest
    @ValueSource(ints = {InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER})
    void mouseFocusedButtonStillTakesEnter(int keyCode) {
        TestScreen screen = TestScreen.create(false);
        AtomicInteger presses = new AtomicInteger();
        focusButton(screen, presses);

        assertTrue(screen.keyPressed(keyCode, 0, 0));
        assertEquals(1, presses.get());
        assertEquals(List.of(), screen.movementKeys);
    }

    @Test
    void otherMovementKeysStillMoveWhileAButtonIsFocused() {
        TestScreen screen = TestScreen.create(true);
        AtomicInteger presses = new AtomicInteger();
        focusButton(screen, presses);

        assertTrue(screen.keyPressed(InputConstants.KEY_W, 0, 0));
        assertEquals(0, presses.get());
        assertEquals(List.of(InputConstants.KEY_W), screen.movementKeys);
    }

    @Test
    void inactiveFocusedButtonLeavesSpaceToMovement() {
        TestScreen screen = TestScreen.create(true);
        AtomicInteger presses = new AtomicInteger();
        focusButton(screen, presses).active = false;

        assertTrue(screen.keyPressed(InputConstants.KEY_SPACE, 0, 0));
        assertEquals(0, presses.get());
        assertEquals(List.of(InputConstants.KEY_SPACE), screen.movementKeys);
    }

    private static IconButton focusButton(TestScreen screen, AtomicInteger presses) {
        IconButton button = new IconButton(0, 0, 16, 16, Component.literal("Button"), null, presses::incrementAndGet);
        screen.setFocused(button);
        return button;
    }

    /** Binds jump to Space and forward to W, recording forwarded keys instead of pressing key mappings. */
    private static final class TestScreen extends MovementAllowedScreen {
        private List<Integer> movementKeys;
        private boolean keyboardNavigating;

        private TestScreen() {
            super(Component.empty());
        }

        /** Skips the field initializers, which read the Minecraft instance absent in unit tests. */
        private static TestScreen create(boolean keyboardNavigating) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                TestScreen screen = (TestScreen) unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), TestScreen.class);
                screen.acceptMovementKeys(true);
                screen.movementKeys = new ArrayList<>();
                screen.keyboardNavigating = keyboardNavigating;
                return screen;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to create a screen test double", e);
            }
        }

        @Override
        boolean isKeyboardNavigating() {
            return this.keyboardNavigating;
        }

        @Override
        boolean testMovementKeysDown(int keyCode) {
            if (keyCode != InputConstants.KEY_SPACE && keyCode != InputConstants.KEY_W) {
                return false;
            }
            this.movementKeys.add(keyCode);
            return true;
        }

        @Override
        protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        int getContentWidth() {
            return 0;
        }

        @Override
        int getContentHeight() {
            return 0;
        }
    }
}
