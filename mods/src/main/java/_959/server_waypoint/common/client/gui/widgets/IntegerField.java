package _959.server_waypoint.common.client.gui.widgets;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class IntegerField extends TranslucentTextField {
    protected int defaultValue;
    protected final int minValue;
    protected final int maxValue;
    protected Consumer<Integer> valueEnteredCallback;

    public IntegerField(int x, int y, int width, int minValue, int maxValue, int defaultValue, Component text, Font textRenderer) {
        super(x, y, width, text, textRenderer);
        this.maxValue = maxValue;
        this.minValue = minValue;
        this.defaultValue = defaultValue;
        this.setFilter(this::testInRange);
    }

    public IntegerField(int x, int y, int width, Component text, Font textRenderer) {
        this(x, y, width, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, text, textRenderer);
    }

    public void setValueEnteredCallback(Consumer<Integer> callback) {
        this.valueEnteredCallback = callback;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && super.getValue().isEmpty()) {
            this.setValue(Integer.toString(this.defaultValue));
            if (this.valueEnteredCallback != null) this.valueEnteredCallback.accept(this.defaultValue);
        }
    }

    @Override
    public void insertText(String text) {
        if (text.isEmpty()) super.insertText(text);
        else if (text.matches("-?[0-9]+")) super.insertText(text);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int prev = getIntValue();
        boolean bl = super.keyPressed(keyCode, scanCode, modifiers);
        int current = this.getIntValue();
        if (bl && this.valueEnteredCallback != null && prev != current) {
            this.valueEnteredCallback.accept(current);
        }
        return bl;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.canConsumeInput()) {
            return false;
        }
        switch (chr) {
            case '-' -> {
                String currentValue = super.getValue();
                if (currentValue.isEmpty()) {
                    return false;
                }
                if (currentValue.charAt(0) != '-') {
                        this.setValue("-" + currentValue);
                        if (this.valueEnteredCallback != null) {
                            this.valueEnteredCallback.accept(this.getIntValue());
                        }
                        return true;
                }
                return false;
            }
            case '+' -> {
                String currentValue = super.getValue();
                if (currentValue.isEmpty()) {
                    return false;
                }
                if (currentValue.charAt(0) == '-') {
                        this.setValue(currentValue.substring(1));
                        if (this.valueEnteredCallback != null) {
                            this.valueEnteredCallback.accept(this.getIntValue());
                        }
                        return true;
                }
                return false;
            }
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                this.insertText(Character.toString(chr));
                if (this.valueEnteredCallback != null) {
                    this.valueEnteredCallback.accept(this.getIntValue());
                }
                return true;
            }
            default -> {return false;}
        }
    }

    public int getIntValue() {
        try {
            return Integer.parseInt(super.getValue());
        } catch (NumberFormatException e) {
            return this.defaultValue;
        }
    }

    private boolean testInRange(String text) {
        if (text.isEmpty()) {
            return true;
        }
        if (text.equals("-")) {
            setValue("");
            return false;
        }
        int n;
        try {
            n = Integer.parseInt(text);
            return this.minValue <= n && n <= this.maxValue;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
