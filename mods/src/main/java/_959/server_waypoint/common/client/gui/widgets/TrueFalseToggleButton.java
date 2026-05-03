package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.network.chat.Component;

public class TrueFalseToggleButton extends ToggleButton {
    private static final int FALSE_COLOR = 0xFFAA0000;
    private static final int TRUE_COLOR = 0xFF00AA00;

    public TrueFalseToggleButton(int x, int y, ToggleButtonCallback callback) {
        super(x, y, 50, 11, Component.translatable("server_waypoint.config.false"), Component.translatable("server_waypoint.config.true"), FALSE_COLOR, TRUE_COLOR, callback);
    }
}
