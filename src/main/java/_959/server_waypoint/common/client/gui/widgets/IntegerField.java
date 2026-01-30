package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class IntegerField extends TranslucentTextField {
    protected int defaultValue;
    protected final int minValue;
    protected final int maxValue;
    protected Consumer<Integer> valueEnteredCallback;

    public IntegerField(int x, int y, int width, int minValue, int maxValue, int defaultValue, Text text, TextRenderer textRenderer) {
        super(x, y, width, text, textRenderer);
        this.maxValue = maxValue;
        this.minValue = minValue;
        this.defaultValue = defaultValue;
        this.setTextPredicate(this::testInRange);
    }

    public IntegerField(int x, int y, int width, Text text, TextRenderer textRenderer) {
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
        if (!focused && this.getText().isEmpty()) {
            this.setText(Integer.toString(this.defaultValue));
            if (this.valueEnteredCallback != null) this.valueEnteredCallback.accept(this.defaultValue);
        }
    }

    @Override
    public void write(String text) {
        if (text.isEmpty()) super.write(text);
        else if (text.matches("-?[0-9]+")) super.write(text);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int prev = getValue();
        boolean bl = super.keyPressed(keyCode, scanCode, modifiers);
        int current = this.getValue();
        if (bl && this.valueEnteredCallback != null && prev != current) {
            this.valueEnteredCallback.accept(current);
        }
        return bl;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.isActive()) {
            return false;
        }
        switch (chr) {
            case '-' -> {
                String currentValue = this.getText();
                if (currentValue.isEmpty()) {
                    return false;
                }
                if (currentValue.charAt(0) != '-') {
                        this.setText("-" + currentValue);
                        if (this.valueEnteredCallback != null) {
                            this.valueEnteredCallback.accept(this.getValue());
                        }
                        return true;
                }
                return false;
            }
            case '+' -> {
                String currentValue = this.getText();
                if (currentValue.isEmpty()) {
                    return false;
                }
                if (currentValue.charAt(0) == '-') {
                        this.setText(currentValue.substring(1));
                        if (this.valueEnteredCallback != null) {
                            this.valueEnteredCallback.accept(this.getValue());
                        }
                        return true;
                }
                return false;
            }
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                this.write(Character.toString(chr));
                if (this.valueEnteredCallback != null) {
                    this.valueEnteredCallback.accept(this.getValue());
                }
                return true;
            }
            default -> {return false;}
        }
    }

    public int getValue() {
        try {
            return Integer.parseInt(this.getText());
        } catch (NumberFormatException e) {
            return this.defaultValue;
        }
    }

    private boolean testInRange(String text) {
        if (text.isEmpty()) {
            return true;
        }
        if (text.equals("-")) {
            setText("");
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
