package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextInputViewportTest {
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

    @ParameterizedTest
    @EnumSource(InputKind.class)
    void populatedUnfocusedFieldShowsTheBeginningOfLongText(InputKind kind) throws Exception {
        SuggestingTextInput field = field(kind);

        field.setValue("minecraft:overworld");

        assertEquals("minecr", visibleText(field));
        assertEquals("minecraft:overworld", field.getValue());
        assertEquals("", field.getHighlighted());
    }

    @ParameterizedTest
    @EnumSource(InputKind.class)
    void losingFocusRestoresTheBeginningAfterEditingAtTheEnd(InputKind kind) throws Exception {
        SuggestingTextInput field = field(kind);
        field.setFocused(true);
        field.setValue("minecraft:overworld");
        assertEquals("rworld", visibleText(field));

        field.setFocused(false);

        assertEquals("minecr", visibleText(field));
        assertEquals("minecraft:overworld", field.getValue());
        assertEquals("", field.getHighlighted());
    }

    @ParameterizedTest
    @EnumSource(InputKind.class)
    void focusedFieldStillScrollsWithTheCursor(InputKind kind) throws Exception {
        SuggestingTextInput field = field(kind);
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

    @Test
    void comboboxInitialValueShowsThePrefixWithoutCallingTheCallback() throws Exception {
        List<String> changes = new ArrayList<>();
        ComboBoxWidget combo = new ComboBoxWidget(10, 20, 56, Component.empty(), new TestFont(),
                List.of(), "minecraft:overworld", changes::add);

        assertEquals("minecr", visibleText(comboInput(combo)));
        assertEquals("minecraft:overworld", combo.getValue());
        assertEquals(List.of(), changes);
    }

    private enum InputKind {
        BASE, TRANSLUCENT, COMBOBOX
    }

    private static SuggestingTextInput field(InputKind kind) throws Exception {
        SuggestingTextInput field = switch (kind) {
            case BASE -> new SuggestingTextInput(10, 20, 36, Component.empty(), new TestFont());
            case TRANSLUCENT -> new TranslucentTextField(10, 20, 36, Component.empty(), new TestFont());
            case COMBOBOX -> comboInput(new ComboBoxWidget(10, 20, 56, Component.empty(), new TestFont(),
                    List.of(), "", value -> { }));
        };
        // Keep real vanilla focus transitions without the live client's IME notification.
        field.setEditable(false);
        return field;
    }

    private static SuggestingTextInput comboInput(ComboBoxWidget combo) throws Exception {
        var input = ComboBoxWidget.class.getDeclaredField("input");
        input.setAccessible(true);
        return (SuggestingTextInput) input.get(combo);
    }

    private static String visibleText(SuggestingTextInput field) throws Exception {
        var displayPos = EditBox.class.getDeclaredField("displayPos");
        displayPos.setAccessible(true);
        return new TestFont().plainSubstrByWidth(
                field.getValue().substring(displayPos.getInt(field)), field.getInnerWidth());
    }
}
