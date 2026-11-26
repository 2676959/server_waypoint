package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateFieldTest {
    @Test
    void acceptsMinusAndDigitsInEachCoordinateMode() {
        CoordinateField field = focusedField();

        for (String prefix : List.of("", "~", "^")) {
            field.setValue(prefix);
            assertTrue(field.charTyped('-', 0), "minus after '" + prefix + "'");
            assertEquals(prefix + "-", field.getValue());
            assertTrue(field.charTyped('4', 0));
            assertEquals(prefix + "-4", field.getValue());
        }
    }

    @Test
    void minusCanPrefixAnExistingNumberAndCannotBeRepeated() {
        CoordinateField field = focusedField();

        for (String prefix : List.of("", "~", "^")) {
            field.setValue(prefix + "42");
            assertTrue(field.charTyped('-', 0), "minus with the caret after '" + field.getValue() + "'");
            assertEquals(prefix + "-42", field.getValue());
            assertEquals(field.getValue().length(), field.getCursorPosition());
            assertFalse(field.charTyped('-', 0));
            assertEquals(prefix + "-42", field.getValue());
        }
    }

    @Test
    void minusCanReplaceTheSelectedValue() {
        CoordinateField field = focusedField();
        field.setValue("42");
        field.setHighlightPos(0);

        assertTrue(field.charTyped('-', 0));
        assertEquals("-", field.getValue());
    }

    @Test
    void plusRemovesOnlyTheMinusSignAndKeepsTheCaretBetweenTheSameDigits() {
        CoordinateField field = focusedField();

        for (String prefix : List.of("", "~", "^")) {
            field.setValue(prefix + "-42");
            field.setCursorPosition(prefix.length() + 2);
            field.setHighlightPos(0);

            assertTrue(field.charTyped('+', 0));
            assertEquals(prefix + "42", field.getValue());
            assertEquals(prefix.length() + 1, field.getCursorPosition());
            assertFalse(field.charTyped('+', 0));
            assertEquals(prefix + "42", field.getValue());
        }
    }

    @Test
    void plusCanClearAnIncompleteNegativeValue() {
        CoordinateField field = focusedField();

        for (String prefix : List.of("", "~", "^")) {
            field.setValue(prefix + "-");
            assertTrue(field.charTyped('+', 0));
            assertEquals(prefix, field.getValue());
        }
    }

    private static CoordinateField focusedField() {
        return new CoordinateField(0, 0, 44, Component.literal("X"), new TestFont()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
    }
}
