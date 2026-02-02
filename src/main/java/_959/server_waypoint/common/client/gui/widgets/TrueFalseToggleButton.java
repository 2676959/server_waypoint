package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.text.Text;

public class TrueFalseToggleButton extends ToggleButton {
    private static final int TRUE_COLOR = 0xFF00AA00;
    private static final int FALSE_COLOR = 0xFFAA0000;

    public TrueFalseToggleButton(int x, int y, ToggleButtonCallback callback) {
        super(x, y, 50, 11, Text.literal("False"), Text.literal("True"), FALSE_COLOR, TRUE_COLOR, callback);
    }
}
