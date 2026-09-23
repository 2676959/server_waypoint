//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A non-interactive themed line; use width/height to choose its length and thickness. */
public final class SeparatorWidget extends ShiftableWidget {
    private final IntSupplier color;
    private boolean visible = true;

    public SeparatorWidget(int x, int y, int width, int height, WidgetThemeVariable color) {
        this(x, y, width, height, WidgetThemeColors.getColorSupplier(color));
    }

    public SeparatorWidget(int x, int y, int width, int height, IntSupplier color) {
        super(x, y, width, height);
        this.color = Objects.requireNonNull(color, "color");
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (this.visible && this.getWidth() > 0 && this.getHeight() > 0) {
            context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(), this.color.getAsInt());
        }
    }
}
