package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.util.CoordinateInputParser;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A coordinate expression with an optional relative or local prefix. */
public class CoordinateField extends TranslucentTextField {
    private int defaultValue;
    private String lastAcceptedValue = "";
    private Consumer<CoordinateField> valueChangedCallback;

    public CoordinateField(int x, int y, int width, Component text, Font textRenderer) {
        super(x, y, width, text, textRenderer);
        this.setResponder(ignored -> this.notifyValueChanged());
    }

    public void setValueChangedCallback(Consumer<CoordinateField> valueChangedCallback) {
        this.valueChangedCallback = valueChangedCallback;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            String value = this.getValue().trim();
            if (value.isEmpty() || !CoordinateInputParser.isCoordinateExpression(value)) {
                if (value.startsWith("^") || value.startsWith("~")) {
                    value = value.substring(0, 1);
                } else {
                    value = Integer.toString(this.defaultValue);
                }
            }
            if (!value.equals(this.getValue())) {
                this.setValue(value);
            }
        }
    }

    @Override
    public void insertText(String text) {
        String previousValue = this.getValue();
        int previousCursor = this.getCursorPosition();
        super.insertText(text);
        if (!isPartialCoordinateExpression(this.getValue())) {
            this.setValue(previousValue);
            this.setCursorPosition(previousCursor);
            this.setHighlightPos(previousCursor);
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.canConsumeInput()) {
            return false;
        }
        return switch (chr) {
            case '-' -> this.insertMinus();
            case '+' -> this.removeMinus();
            case '~', '^', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> this.insertCharacter(chr);
            default -> false;
        };
    }

    private boolean insertMinus() {
        if (!this.getHighlighted().isEmpty()) {
            return this.insertCharacter('-');
        }

        String value = this.getValue();
        int signPosition = signPosition(value);
        if (value.startsWith("-", signPosition)) {
            return false;
        }

        int cursor = this.getCursorPosition();
        this.setCursorPosition(signPosition);
        this.setHighlightPos(signPosition);
        boolean inserted = this.insertCharacter('-');
        int updatedCursor = inserted ? Math.max(cursor, signPosition) + 1 : cursor;
        this.setCursorPosition(updatedCursor);
        this.setHighlightPos(updatedCursor);
        return inserted;
    }

    private boolean removeMinus() {
        String value = this.getValue();
        int signPosition = signPosition(value);
        if (!value.startsWith("-", signPosition)) {
            return false;
        }

        int cursor = this.getCursorPosition();
        this.setValue(value.substring(0, signPosition) + value.substring(signPosition + 1));
        int updatedCursor = cursor > signPosition ? cursor - 1 : cursor;
        this.setCursorPosition(updatedCursor);
        this.setHighlightPos(updatedCursor);
        return true;
    }

    private static int signPosition(String value) {
        return value.startsWith("~") || value.startsWith("^") ? 1 : 0;
    }

    private boolean insertCharacter(char chr) {
        String previousValue = super.getValue();
        this.insertText(Character.toString(chr));
        return !previousValue.equals(super.getValue());
    }

    private static boolean isPartialCoordinateExpression(String text) {
        return text.matches("[~^]?-?[0-9]*");
    }

    @Override
    protected boolean shouldShowSuggestion(String suggestion, String value, String lowerValue) {
        return true;
    }

    private void notifyValueChanged() {
        String value = this.getValue();
        if (value.equals(this.lastAcceptedValue) || !isPartialCoordinateExpression(value)) {
            return;
        }
        this.lastAcceptedValue = value;
        if (this.valueChangedCallback != null) {
            this.valueChangedCallback.accept(this);
        }
    }
}
