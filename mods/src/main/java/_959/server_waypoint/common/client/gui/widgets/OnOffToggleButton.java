package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ToggleButtonCallback;

import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS_BACKGROUND;

/** A 50×11 toggle that reads On or Off. */
public class OnOffToggleButton extends ToggleButton {
    public OnOffToggleButton(int x, int y, ToggleButtonCallback callback) {
        super(
                x,
                y,
                50,
                11,
                Component.translatable("server_waypoint.config.off"),
                Component.translatable("server_waypoint.config.on"),
                DANGER_BACKGROUND,
                SUCCESS_BACKGROUND,
                callback
        );
    }
}
