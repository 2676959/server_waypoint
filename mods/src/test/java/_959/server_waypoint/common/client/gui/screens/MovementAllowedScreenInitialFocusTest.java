//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

//? if >= 1.20.5 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After every rebuild, such as a resize, vanilla moves focus to the next Tab stop when the keyboard
 * was used last. An open modal keeps its focus instead, so a dialog's Cancel button doesn't hand it
 * to the confirm button. Versions before 1.20.5 pick no focus after a rebuild.
 */
class MovementAllowedScreenInitialFocusTest {
    @Test
    void aRebuildLeavesFocusWithAnOpenModal() {
        TestScreen screen = TestScreen.create(true);
        Listener cancel = new Listener();
        screen.setFocused(cancel);

        screen.setInitialFocus();

        assertFalse(screen.vanillaPickedFocus);
        assertSame(cancel, screen.getFocused());
    }

    @Test
    void aRebuildLetsVanillaPickFocusWithoutAModal() {
        TestScreen screen = TestScreen.create(false);

        screen.setInitialFocus();

        assertTrue(screen.vanillaPickedFocus);
    }

    /** Records vanilla's pick instead of reading the game's last input type. */
    private static final class TestScreen extends MovementAllowedScreen {
        private boolean modalOpen;
        private boolean vanillaPickedFocus;

        // Never runs: create() skips constructors, which read the Minecraft instance absent in unit tests.
        private TestScreen() {
            super(Component.empty());
        }

        private static TestScreen create(boolean modalOpen) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                TestScreen screen = (TestScreen) unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), TestScreen.class);
                screen.modalOpen = modalOpen;
                return screen;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to create a screen test double", e);
            }
        }

        @Override
        protected boolean hasOpenModal() {
            return this.modalOpen;
        }

        @Override
        void pickInitialFocus() {
            this.vanillaPickedFocus = true;
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

    private static final class Listener implements GuiEventListener {
        private boolean focused;

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focused;
        }
    }
}
//?}
