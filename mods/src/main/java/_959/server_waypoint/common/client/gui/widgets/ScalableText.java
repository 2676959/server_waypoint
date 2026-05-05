package _959.server_waypoint.common.client.gui.widgets;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;

public class ScalableText extends ShiftableWidget {
    private final Font textRenderer;
    private Component text;
    private float scale;
    private int color;
    private final int maxWidth;
    private volatile List<FormattedCharSequence> warpLines = List.of();

    public ScalableText(int x, int y, Component text, int color, Font textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, int color, Font textRenderer) {
        this(x, y, text, scale, color, -1, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, int color, int maxWidth, Font textRenderer) {
        super(x, y, Math.round(textRenderer.width(text) * scale), Math.round(textRenderer.lineHeight * scale));
        this.text = text;
        this.scale = scale;
        this.color = color;
        this.maxWidth = maxWidth;
        this.textRenderer = textRenderer;
        if (maxWidth != -1) {
            this.warpLines = textRenderer.split(text, maxWidth);
        }
    }

    public void setMaxWidth(int width) {
        if (maxWidth == -1) return;
        this.warpLines = this.textRenderer.split(this.text, width);
    }

    @Override
    public int getWidth() {
        return Math.round((this.maxWidth == -1 ? this.textRenderer.width(this.text) : this.maxWidth) * this.scale);
    }

    @Override
    public int getHeight() {
        return Math.round((this.maxWidth == -1 ? 1 : this.warpLines.size()) * this.textRenderer.lineHeight * this.scale);
    }

    public void setText(Component text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = Component.nullToEmpty(text);
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        push(context);
        translate(context, this.getShiftedX(), this.getShiftedY());
        scale(context, this.scale, this.scale);
        if (this.maxWidth == -1) {
            context.drawString(this.textRenderer, this.text, 0, 0, this.color, true);
        } else {
            for (int i = 0; i < this.warpLines.size(); i++) {
                context.drawString(this.textRenderer, this.warpLines.get(i), 0, i * this.textRenderer.lineHeight, this.color, true);
            }
        }
        pop(context);
    }
}
