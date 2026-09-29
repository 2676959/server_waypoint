//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerField;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import com.mojang.blaze3d.platform.InputConstants;
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
    void escapeUnfocusesAComboBoxWithoutAnOpenPopupBeforeClosingTheScreen() throws ReflectiveOperationException {
        ComboBoxWidget comboBox = allocate(ComboBoxWidget.class);
        var inputField = ComboBoxWidget.class.getDeclaredField("input");
        inputField.setAccessible(true);
        EditBox input = (EditBox) allocate(inputField.getType());
        input.setCanLoseFocus(true);
        inputField.set(comboBox, input);
        assertEscapeLeavesTextEntry(comboBox);
        assertFalse(input.isFocused());
    }

    @Test
    void escapeLeavesAnIntegerSliderNumberFieldBeforeClosingTheScreen() throws ReflectiveOperationException {
        FocusOnlyField field = allocate(FocusOnlyField.class);
        IntegerSlider slider = integerSlider(field, field);
        assertEscapeLeavesTextEntry(slider);
        assertFalse(field.isFocused());
    }

    @Test
    void escapeClosesTheScreenWhenAnIntegerSliderTrackHasFocus() throws ReflectiveOperationException {
        FocusOnlyListener track = new FocusOnlyListener();
        IntegerSlider slider = integerSlider(allocate(FocusOnlyField.class), track);
        TestScreen screen = TestScreen.create();
        screen.setFocused(slider);
        assertTrue(track.isFocused());

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(screen.closed);
    }

    /** An {@code IntegerSlider} whose number field and selected part are test doubles. */
    private static IntegerSlider integerSlider(IntegerField field, GuiEventListener selectedPart)
            throws ReflectiveOperationException {
        IntegerSlider slider = allocate(IntegerSlider.class);
        setField(slider, "integerField", field);
        setField(slider, "focused", selectedPart);
        return slider;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
