package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.util.CoordinateInputParser;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CoordinateField extends IntegerField {
    private Consumer<CoordinateField> valueChangedCallback;

    public CoordinateField(int x, int y, int width, Component text, Font textRenderer) {
        super(x, y, width, text, textRenderer);
    }

    public void setValueChangedCallback(Consumer<CoordinateField> valueChangedCallback) {
        this.valueChangedCallback = valueChangedCallback;
    }

    @Override
    public void setValue(String value) {
        String previousValue = super.getValue();
        super.setValue(value);
        if (!previousValue.equals(super.getValue())) {
            this.notifyValueChanged();
        }
    }

    @Override
    public void setFocused(boolean focused) {
        String value = super.getValue();
        super.setFocused(focused);
        if (!focused && CoordinateInputParser.isCoordinateExpression(value)) {
            this.setValue(value.trim());
        }
    }

    @Override
    public void insertText(String text) {
        String previousValue = super.getValue();
        super.insertText(text);
        if (!isPartialCoordinateExpression(super.getValue())) {
            this.setValue(previousValue);
        } else if (!previousValue.equals(super.getValue())) {
            this.notifyValueChanged();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        String previousValue = super.getValue();
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled && !previousValue.equals(super.getValue())) {
            this.notifyValueChanged();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String previousValue = super.getValue();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && !previousValue.equals(super.getValue())) {
            this.notifyValueChanged();
        }
        return handled;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.canConsumeInput()) {
            return false;
        }
        switch (chr) {
            case '~', '^' -> {
                return insertCharacter(chr);
            }
            case '-' -> {
                return insertCharacter(chr);
            }
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                return insertCharacter(chr);
            }
            default -> {
                return false;
            }
        }
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
        if (this.valueChangedCallback != null) {
            this.valueChangedCallback.accept(this);
        }
    }
}
