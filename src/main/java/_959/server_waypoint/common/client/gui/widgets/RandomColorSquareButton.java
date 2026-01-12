package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.util.ColorUtils.randomColor;

public class RandomColorSquareButton extends ColorSquareButton {
    public RandomColorSquareButton(int x, int y, int size, boolean renderBorder, Runnable callback) {
        super(x, y, size, 0xFF000000 | randomColor(), renderBorder, callback);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (isFocused()) {
            this.color = 0xFF000000 | randomColor();
        }
        this.callback.run();
    }
}
