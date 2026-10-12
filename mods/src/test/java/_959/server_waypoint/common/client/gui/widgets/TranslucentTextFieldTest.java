package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslucentTextFieldTest {
    @Test
    void outlineAnchorPositionsTextInsideOutlinePadding() {
        assertEquals(12, AnchorMode.OUTLINE.getContentX(10, TranslucentTextField.OUTLINE_PADDING));
        assertEquals(22, AnchorMode.OUTLINE.getContentY(20, TranslucentTextField.OUTLINE_PADDING));
    }

    @Test
    void textAreaAnchorKeepsExistingTextPosition() {
        assertEquals(10, AnchorMode.CONTENT.getContentX(10, TranslucentTextField.OUTLINE_PADDING));
        assertEquals(20, AnchorMode.CONTENT.getContentY(20, TranslucentTextField.OUTLINE_PADDING));
    }

    @Test
    void populatedUnfocusedFieldShowsTheBeginningOfLongText() throws Exception {
        TranslucentTextField field = field();

        field.setValue("minecraft:overworld");

        assertEquals("minecr", visibleText(field));
        assertEquals("minecraft:overworld", field.getValue());
        assertEquals("", field.getHighlighted());
    }

    @Test
    void losingFocusRestoresTheBeginningAfterEditingAtTheEnd() throws Exception {
        TranslucentTextField field = field();
        field.setFocused(true);
        field.setValue("minecraft:overworld");
        assertEquals("rworld", visibleText(field));

        field.setFocused(false);

        assertEquals("minecr", visibleText(field));
        assertEquals("minecraft:overworld", field.getValue());
        assertEquals("", field.getHighlighted());
    }

    @Test
    void focusedFieldStillScrollsWithTheCursor() throws Exception {
        TranslucentTextField field = field();
        field.setFocused(true);
        field.setValue("minecraft:overworld");
        assertEquals("rworld", visibleText(field));

        field.setCursorPosition(0);
        field.setHighlightPos(0);
        assertEquals("minecr", visibleText(field));
        field.setCursorPosition(field.getValue().length());
        field.setHighlightPos(field.getValue().length());
        assertEquals("rworld", visibleText(field));
    }

    private static TranslucentTextField field() {
        TranslucentTextField field = new TranslucentTextField(10, 20, 36, Component.empty(), new TestFont());
        // Keep real vanilla focus transitions without the live client's IME notification.
        field.setEditable(false);
        return field;
    }

    private static String visibleText(TranslucentTextField field) throws Exception {
        var displayPos = EditBox.class.getDeclaredField("displayPos");
        displayPos.setAccessible(true);
        return new TestFont().plainSubstrByWidth(
                field.getValue().substring(displayPos.getInt(field)), field.getInnerWidth());
    }
}
