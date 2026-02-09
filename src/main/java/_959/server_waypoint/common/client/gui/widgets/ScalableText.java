package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public class ScalableText extends ShiftableWidget {
    private final TextRenderer textRenderer;
    private Text text;
    private float scale;
    private int color;
    private final int maxWidth;
    private volatile List<OrderedText> warpLines = List.of();

    public ScalableText(int x, int y, Text text, int color, TextRenderer textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Text text, float scale, int color, TextRenderer textRenderer) {
        this(x, y, text, scale, color, -1, textRenderer);
    }

    public ScalableText(int x, int y, Text text, float scale, int color, int maxWidth, TextRenderer textRenderer) {
        super(x, y, Math.round(textRenderer.getWidth(text) * scale), Math.round(textRenderer.fontHeight * scale));
        this.text = text;
        this.scale = scale;
        this.color = color;
        this.maxWidth = maxWidth;
        this.textRenderer = textRenderer;
        if (maxWidth != -1) {
            this.warpLines = textRenderer.wrapLines(text, maxWidth);
        }
    }

    public void setMaxWidth(int width) {
        if (maxWidth == -1) return;
        this.warpLines = this.textRenderer.wrapLines(this.text, width);
    }

    @Override
    public int getWidth() {
        return Math.round((this.maxWidth == -1 ? this.textRenderer.getWidth(this.text) : this.maxWidth) * this.scale);
    }

    @Override
    public int getHeight() {
        return Math.round((this.maxWidth == -1 ? 1 : this.warpLines.size()) * this.textRenderer.fontHeight * this.scale);
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
        if (this.maxWidth == -1) {
            context.drawText(this.textRenderer, this.text, 0, 0, this.color, true);
        } else {
            for (int i = 0; i < this.warpLines.size(); i++) {
                context.drawText(this.textRenderer, this.warpLines.get(i), 0, i * this.textRenderer.fontHeight, this.color, true);
            }
        }
        matrixStack.pop();
    }
}
