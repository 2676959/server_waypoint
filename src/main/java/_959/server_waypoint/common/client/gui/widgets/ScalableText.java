package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class ScalableText extends ShiftableWidget implements Drawable {
    private final TextRenderer textRenderer;
    private Text text;
    private float scale;
    private int color;

    public ScalableText(int x, int y, Text text, int color, TextRenderer textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Text text, float scale, int color, TextRenderer textRenderer) {
        super(x, y, Math.round(textRenderer.getWidth(text) * scale), Math.round(textRenderer.fontHeight * scale));
        this.text = text;
        this.scale = scale;
        this.color = color;
        this.textRenderer = textRenderer;
    }

    @Override
    public int getWidth() {
        return Math.round(this.textRenderer.getWidth(this.text) * this.scale);
    }

    @Override
    public int getHeight() {
        return Math.round(this.textRenderer.fontHeight * this.scale);
    }

    public void setText(Text text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = Text.of(text);
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        MatrixStack matrixStack = context.getMatrices();
        matrixStack.push();
        matrixStack.translate(this.getShiftedX(), this.getShiftedY(), 0.0F);
        matrixStack.scale(this.scale, this.scale, 1.0F);
        context.drawText(this.textRenderer, this.text, 0, 0, this.color, true);
        matrixStack.pop();
    }
}
