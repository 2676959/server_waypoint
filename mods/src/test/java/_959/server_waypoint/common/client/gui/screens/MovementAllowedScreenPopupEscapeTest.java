//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerField;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAllowedScreenPopupEscapeTest {
    @Test
    void escapeUnfocusesAnEditBoxBeforeClosingTheScreen() {
        EditBox input = allocate(EditBox.class);
        input.setCanLoseFocus(true);
        assertEscapeLeavesTextEntry(input);
    }

    @Test
    void escapeUnfocusesAComboBoxWithoutAnOpenPopupBeforeClosingTheScreen() {
        assertEscapeLeavesTextEntry(allocate(ClosedComboBox.class));
    }

    @Test
    void escapeLeavesAnIntegerSliderNumberFieldBeforeClosingTheScreen() {
        FocusOnlyField field = allocate(FocusOnlyField.class);
        IntegerSlider slider = new TestIntegerSlider(field);
        assertEscapeLeavesTextEntry(slider);
        assertFalse(field.isFocused());
    }

    @Test
    void escapeClosesTheScreenWhenAnIntegerSliderTrackHasFocus() {
        IntegerSlider slider = new TestIntegerSlider(allocate(FocusOnlyField.class));
        TestScreen screen = TestScreen.create();
        screen.setFocused(slider);
        // What a click on the track does.
        FocusOnlyListener track = new FocusOnlyListener();
        slider.updateFocused(track);
        assertTrue(track.isFocused());

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    private static void assertEscapeLeavesTextEntry(GuiEventListener input) {
        TestScreen screen = TestScreen.create();
        screen.setFocused(input);
        assertTrue(input.isFocused());

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertNull(screen.getFocused());
        assertFalse(input.isFocused());
        assertFalse(screen.closed);

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    /** Skips constructors requiring the Minecraft client; the test only exercises focus and Escape. */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create an input test double", e);
        }
    }

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

    /**
     * A combo box with no popup open. {@code allocate} skips its constructor: from 26.1, its text field
     * asks the game to start text input when it takes focus. Without that field, this double answers
     * the one question the screen would ask it, and reports no suggestions open.
     */
    private static final class ClosedComboBox extends ComboBoxWidget {
        // Never runs: the tests create this double with allocate(), which skips constructors.
        private ClosedComboBox() {
            super(0, 0, 0, Component.empty(), null, List.of(), "", value -> {
            });
        }

        @Override
        public boolean closeSuggestionsIfOpen() {
            return false;
        }
    }

    /** A slider around a number-field double, through {@code IntegerSlider}'s protected constructor. */
    private static final class TestIntegerSlider extends IntegerSlider {
        private TestIntegerSlider(IntegerField field) {
            super(0, 0, 100, field, value -> {
            });
        }
    }

    /** A number field that only records focus; {@code allocate} skips the text state a real one needs. */
    private static final class FocusOnlyField extends IntegerField {
        private boolean focusedForTest;

        // Never runs: the tests create this double with allocate(), which skips constructors.
        private FocusOnlyField() {
            super(0, 0, 0, Component.empty(), null);
        }

        @Override
        public void setFocused(boolean focused) {
            this.focusedForTest = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focusedForTest;
        }
    }

    /** Stands in for the slider track, which only needs to take and lose focus here. */
    private static final class FocusOnlyListener implements GuiEventListener {
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
