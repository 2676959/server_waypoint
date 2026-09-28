//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAllowedScreenPopupEscapeTest {
    @Test
    void escapeClosesTheFocusedPopupBeforeTheScreen() {
        TestDropdown dropdown = new TestDropdown();
        TestScreen screen = TestScreen.create();
        screen.setFocused(dropdown);
        dropdown.setExpanded(true);

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertFalse(dropdown.isExpanded());
        assertNull(screen.getFocused());
        assertFalse(screen.closed);

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    @Test
    void escapeClosesTheScreenWhenTheFocusedWidgetHasNoOpenPopup() {
        TestDropdown dropdown = new TestDropdown();
        TestScreen screen = TestScreen.create();
        screen.setFocused(dropdown);

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    /** Records closing instead of switching screens; vanilla handles the Escape key itself. */
    private static final class TestScreen extends MovementAllowedScreen {
        private boolean closed;

        private TestScreen() {
            super(Component.empty());
        }

        /** Skips the field initializers, which read the Minecraft instance absent in unit tests. */
        private static TestScreen create() {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                TestScreen screen = (TestScreen) unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), TestScreen.class);
                screen.acceptMovementKeys(true);
                return screen;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to create a screen test double", e);
            }
        }

        @Override
        public void onClose() {
            this.closed = true;
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

    private static final class TestDropdown extends AbstractDropdownMenuWidget {
        private TestDropdown() {
            super(0, 0, 16, 16, Component.literal("Dropdown"),
                    LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
            this.addMenuItem(new TestMenuItem());
        }

        @Override
        protected void renderDropdownControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }

    private static final class TestMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem {
        private TestMenuItem() {
            super(16, 16, Component.literal("Item"));
        }

        @Override
        protected void onSelected() {
        }

        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }
}
