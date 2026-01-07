package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class IntegerField extends TranslucentTextField {
    public IntegerField(TextRenderer textRenderer, int x, int y, int width, Text text) {
        super(textRenderer, x, y, width, text);
    }

    @Override
    public void write(String text) {
        if (text.isEmpty()) super.write(text);
        else if (text.matches("-?[0-9]+")) super.write(text);
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
                    return true;
                }
                return false;
            }
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                this.write(Character.toString(chr));
                return true;
            }
            default -> {return false;}
        }
    }
}
