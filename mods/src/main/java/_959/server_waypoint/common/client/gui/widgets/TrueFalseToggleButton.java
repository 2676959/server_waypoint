package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ToggleButtonCallback;

import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS_BACKGROUND;

public class TrueFalseToggleButton extends ToggleButton {
    public TrueFalseToggleButton(int x, int y, ToggleButtonCallback callback) {
        super(
                x,
                y,
                50,
                11,
                Component.translatable("server_waypoint.config.false"),
                Component.translatable("server_waypoint.config.true"),
                DANGER_BACKGROUND,
                SUCCESS_BACKGROUND,
                callback
        );
    }
}
