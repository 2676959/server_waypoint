//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.render.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;

public class ScalableText extends ShiftableWidget implements Expandable {
    private final Font textRenderer;
    private Component text;
    private float scale;
    private IntSupplier colorSupplier;
    private int maxWidth;
    private volatile List<FormattedCharSequence> warpLines = List.of();

    public ScalableText(int x, int y, Component text, int color, Font textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Component text, WidgetThemeVariable color, Font textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Component text, IntSupplier color, Font textRenderer) {
        this(x, y, text, 1, color, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, int color, Font textRenderer) {
        this(x, y, text, scale, color, -1, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, WidgetThemeVariable color, Font textRenderer) {
        this(x, y, text, scale, color, -1, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, IntSupplier color, Font textRenderer) {
        this(x, y, text, scale, color, -1, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, int color, int maxWidth, Font textRenderer) {
        this(x, y, text, scale, () -> color, maxWidth, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, WidgetThemeVariable color, int maxWidth, Font textRenderer) {
        this(x, y, text, scale, WidgetThemeColors.getColorSupplier(color), maxWidth, textRenderer);
    }

    public ScalableText(int x, int y, Component text, float scale, IntSupplier color, int maxWidth, Font textRenderer) {
        super(x, y, Math.round(textRenderer.width(text) * scale), Math.round(textRenderer.lineHeight * scale));
        this.text = text;
        this.scale = scale;
        this.colorSupplier = Objects.requireNonNull(color, "color");
        this.maxWidth = maxWidth;
        this.textRenderer = textRenderer;
        if (maxWidth != -1) {
            this.warpLines = textRenderer.split(text, maxWidth);
        }
    }

    public void setMaxWidth(int width) {
        this.maxWidth = Math.max(0, width);
        this.warpLines = this.textRenderer.split(this.text, this.maxWidth);
    }

    @Override
    public void setWidth(int width) {
        this.setMaxWidth(Math.round(width / this.scale));
    }

    @Override
    public void setHeight(int height) {
    }

    @Override
    public int getWidth() {
        return Math.round((this.maxWidth == -1 ? this.textRenderer.width(this.text) : this.maxWidth) * this.scale);
    }

    @Override
    public int getHeight() {
        int lineCount = this.maxWidth == -1 ? 1 : Math.max(1, this.warpLines.size());
        return Math.round(lineCount * this.textRenderer.lineHeight * this.scale);
    }

    public void setText(Component text) {
        this.text = text;
        if (this.maxWidth != -1) {
            this.warpLines = this.textRenderer.split(this.text, this.maxWidth);
        }
    }

    public void setText(String text) {
        this.setText(Component.nullToEmpty(text));
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setColor(int color) {
        this.colorSupplier = () -> color;
    }

    public void setColor(WidgetThemeVariable color) {
        this.setColor(WidgetThemeColors.getColorSupplier(color));
    }

    public void setColor(IntSupplier color) {
        this.colorSupplier = Objects.requireNonNull(color, "color");
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        push(context);
        translate(context, this.getShiftedX(), this.getShiftedY());
        scale(context, this.scale, this.scale);
        int color = this.colorSupplier.getAsInt();
        if (this.maxWidth == -1) {
            drawText(context, this.textRenderer, this.text, 0, 0, color, true);
        } else {
            for (int i = 0; i < this.warpLines.size(); i++) {
                drawText(context, this.textRenderer, this.warpLines.get(i), 0, i * this.textRenderer.lineHeight, color, true);
            }
        }
        pop(context);
    }
}
