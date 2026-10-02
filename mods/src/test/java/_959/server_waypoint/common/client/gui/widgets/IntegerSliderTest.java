package _959.server_waypoint.common.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Before 1.21.5, vanilla offers every click to every child, so a slider that a scrolled
 * {@link SettingsListWidget} hides must ignore input itself.
 */
class IntegerSliderTest {
    @Test
    void aVisibleSliderPassesInputToItsNumberField() {
        InputRecordingField field = allocate(InputRecordingField.class);
        IntegerSlider slider = slider(field, true);

        assertTrue(slider.mouseClicked(0, 0, 0));
        assertTrue(slider.keyPressed(InputConstants.KEY_1, 0, 0));
        assertTrue(slider.charTyped('1', 0));
        assertEquals(3, field.inputs);
    }

    @Test
    void aHiddenSliderIgnoresClicksKeysAndTyping() {
        InputRecordingField field = allocate(InputRecordingField.class);
        IntegerSlider slider = slider(field, false);

        assertFalse(slider.mouseClicked(0, 0, 0));
        assertFalse(slider.keyPressed(InputConstants.KEY_1, 0, 0));
        assertFalse(slider.charTyped('1', 0));
        assertEquals(0, field.inputs);
    }

    /** An active slider whose number field is {@code field}, which a new slider selects. */
    private static IntegerSlider slider(InputRecordingField field, boolean visible) {
        IntegerSlider slider = new IntegerSlider(0, 0, 100, field, value -> {
        });
        slider.active = true;
        slider.visible = visible;
        return slider;
    }

    /** Skips the number field's constructor, which needs the Minecraft client; the tests only route input. */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create a test double", e);
        }
    }

    /**
     * A number field that accepts and counts every click, key and character, and only records focus:
     * {@code allocate} skips the text state a real field commits when it loses focus.
     */
    private static final class InputRecordingField extends IntegerField {
        private int inputs;
        private boolean focusedForTest;

        // Never runs: the tests create this double with allocate(), which skips constructors.
        private InputRecordingField() {
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.inputs++;
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            this.inputs++;
            return true;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            this.inputs++;
            return true;
        }
    }
}
