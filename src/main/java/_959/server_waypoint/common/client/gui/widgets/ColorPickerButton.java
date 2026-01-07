package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class ColorPickerButton extends ShiftableClickableWidget {
    private int color;

    public ColorPickerButton(int x, int y, int size) {
        super(x, y, size, size, Text.of("Color picker"));
        this.setOffsets(-1, -1);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {

    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int bdColor = isHovered() ? 0xFFFFFFFF : 0x55FFFFFF;
        context.drawBorder(x - 1, y - 1, width + 2, width + 2, bdColor);
        context.fill(x, y, x + width, y + width, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    public int getColor() {
        return this.color;
    }

    public void setColor(int rgb) {
        this.color = 0xFF000000 | rgb;
    }
}
