package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorHexCodeFieldTest {
    private static ColorHexCodeField argb() {
        return ColorHexCodeField.argb(0, 0, Component.literal("Color"), new TestFont());
    }

    /**
     * A field that takes typing, in the ARGB mode when {@code alpha} is true. Focusing an editable box
     * reports it to the game client, which a test doesn't have, so the field takes input without focus.
     */
    private static ColorHexCodeField typingField(boolean alpha) {
        return new ColorHexCodeField(0, 0, Component.literal("Color"), new TestFont(), alpha) {
            @Override
            public boolean canConsumeInput() {
                return true;
            }
        };
    }

    @Test
    void typingOverASelectionReplacesItInAFullArgbField() {
        ColorHexCodeField field = typingField(true);
        field.setColor(0xB31C1C1C);
        field.setCursorPosition(0);
        field.setHighlightPos(8);

        assertTrue(field.charTyped('d', 0), "select all, then type");
        assertEquals("D", field.getValue());

        field.setColor(0xB31C1C1C);
        field.setCursorPosition(2);
        field.setHighlightPos(4);

        assertTrue(field.charTyped('f', 0), "select the red pair, then type over it");
        assertTrue(field.charTyped('f', 0));
        assertEquals("B3FF1C1C", field.getValue());
        assertEquals(0xB3FF1C1C, field.getColor());
    }

    @Test
    void typingOverASelectionReplacesItInAFullRgbField() {
        ColorHexCodeField field = typingField(false);
        field.setColor(0x123456);
        field.setCursorPosition(0);
        field.setHighlightPos(6);

        assertTrue(field.charTyped('a', 0));
        assertEquals("A", field.getValue());
    }

    @Test
    void aFullFieldWithoutASelectionRefusesAnotherDigit() {
        ColorHexCodeField argb = typingField(true);
        argb.setColor(0xB31C1C1C);
        assertFalse(argb.charTyped('0', 0));
        assertEquals("B31C1C1C", argb.getValue());

        ColorHexCodeField rgb = typingField(false);
        rgb.setColor(0x123456);
        assertFalse(rgb.charTyped('0', 0));
        assertEquals("123456", rgb.getValue());
    }

    @Test
    void aPasteLongerThanTheLimitIsCut() {
        ColorHexCodeField argb = argb();
        argb.setValue("");
        argb.insertText("#D9262626FF");
        assertEquals("D9262626", argb.getValue());

        ColorHexCodeField rgb = new ColorHexCodeField(0, 0, Component.literal("RGB"), new TestFont());
        rgb.insertText("#FF00FF00");
        assertEquals("FF00FF", rgb.getValue());
    }

    @Test
    void argbModeShowsAndReturnsAllEightDigits() {
        ColorHexCodeField field = argb();
        field.setColor(0xD9262626);
        assertEquals("D9262626", field.getValue());
        assertEquals(0xD9262626, field.getColor());
    }

    @Test
    void argbModeReadsAnEightDigitValue() {
        ColorHexCodeField field = argb();
        field.setValue("B31C1C1C");
        assertEquals(0xB31C1C1C, field.getColor());
    }

    @Test
    void aResponderReadsTheNewColorOnlyOnceTheEighthDigitIsIn() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("");
        List<Integer> colors = new ArrayList<>();
        field.setResponder(text -> colors.add(field.getColor()));

        field.insertText("D926262");
        field.insertText("6");

        assertEquals(List.of(0xB31C1C1C, 0xD9262626), colors);
    }

    @Test
    void committingSixDigitsKeepsTheCurrentAlpha() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("FF0000");
        field.commit();
        assertEquals("B3FF0000", field.getValue());
        assertEquals(0xB3FF0000, field.getColor());
    }

    @Test
    void committingSixDigitsKeepsTheAlphaOfTheLastCompleteValue() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        // The user replaces the value with FF123456, which applies, then deletes its last two digits.
        field.setValue("");
        field.insertText("FF123456");
        field.setCursorPosition(6);
        field.setHighlightPos(8);
        field.insertText("");
        assertEquals("FF1234", field.getValue());
        assertEquals(0xFF123456, field.getColor());

        field.commit();

        assertEquals("FFFF1234", field.getValue());
        assertEquals(0xFFFF1234, field.getColor());
    }

    @Test
    void committingAnIncompleteValueRestoresTheCurrentColor() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("D92");
        field.commit();
        assertEquals("B31C1C1C", field.getValue());
    }

    @Test
    void losingFocusCommitsTheValue() {
        ColorHexCodeField field = argb();
        field.setColor(0xB31C1C1C);
        field.setValue("FF0000");
        // Vanilla reports an editable box's focus change to the client, which a test doesn't have.
        field.setEditable(false);
        field.setFocused(false);
        assertEquals("B3FF0000", field.getValue());
    }

    @Test
    void aPastedValueMayStartWithAHash() {
        ColorHexCodeField field = argb();
        field.setValue("");
        field.insertText("#D9262626");
        assertEquals("D9262626", field.getValue());
    }

    @Test
    void rgbModeAlsoDropsTheHashOfAPastedValue() {
        ColorHexCodeField field = new ColorHexCodeField(0, 0, Component.literal("RGB"), new TestFont());
        field.insertText("#FF0000");
        assertEquals("FF0000", field.getValue());
    }

    @Test
    void rgbModeStillPadsShortValuesToSixDigits() {
        ColorHexCodeField field = new ColorHexCodeField(0, 0, Component.literal("RGB"), new TestFont());
        field.setValue("12");
        field.commit();
        assertEquals("000012", field.getValue());
    }
}
