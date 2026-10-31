//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.VerticalAlignment;
import _959.server_waypoint.common.client.gui.render.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;

public class ScalableText extends ShiftableWidget implements Expandable, VerticalAlignment {
    private static final Component ELLIPSIS = Component.literal("…");

    private final Font textRenderer;
    private Component text;
    private float scale;
    private IntSupplier colorSupplier;
    private int maxWidth;
    private int maxLines;
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
        this.rewrap();
    }

    public void setMaxWidth(int width) {
        this.maxWidth = Math.max(0, width);
        this.rewrap();
    }

    /**
     * Limits wrapped text to {@code maxLines} lines. Text that needs more is cut so that its last line
     * ends with an ellipsis; zero or less lifts the limit. It only applies while a maximum width is set.
     */
    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(0, maxLines);
        this.rewrap();
    }

    private void rewrap() {
        if (this.maxWidth != -1) {
            this.warpLines = wrap(this.textRenderer, this.text, this.maxWidth, this.maxLines);
        }
    }

    /** {@code text} wrapped to {@code maxWidth}; with a positive {@code maxLines}, cut to fit that many lines. */
    static List<FormattedCharSequence> wrap(Font font, Component text, int maxWidth, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        if (maxLines <= 0 || lines.size() <= maxLines) {
            return lines;
        }
        // The longest start of the text that fits with its ellipsis. More text never needs fewer lines, so
        // a binary search finds it. The cut is made on the logical text, so right-to-left text loses its end.
        String plain = text.getString();
        int low = 0;
        int high = plain.codePointCount(0, plain.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (font.split(startWithEllipsis(text, middle), maxWidth).size() <= maxLines) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return font.split(startWithEllipsis(text, low), maxWidth);
    }

    /** The first {@code length} code points of {@code text}, with their styles, followed by an ellipsis. */
    private static Component startWithEllipsis(Component text, int length) {
        MutableComponent start = Component.empty();
        int[] left = {length};
        text.visit((style, content) -> {
            int count = Math.min(left[0], content.codePointCount(0, content.length()));
            if (count > 0) {
                start.append(Component.literal(content.substring(0, content.offsetByCodePoints(0, count))).withStyle(style));
                left[0] -= count;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return start.append(ELLIPSIS);
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

    @Override
    public float getVerticalAlignmentCenter() {
        // The font's line box includes two trailing pixels below its glyphs.
        int lineCount = this.maxWidth == -1 ? 1 : Math.max(1, this.warpLines.size());
        return (lineCount * this.textRenderer.lineHeight - 2) * this.scale / 2.0F;
    }

    /** The width of the widest wrapped line, scaled like {@link #getWidth()}; the whole text without a maximum width, and 0 with no lines. */
    public int getTextWidth() {
        if (this.maxWidth == -1) {
            return Math.round(this.textRenderer.width(this.text) * this.scale);
        }
        int widest = 0;
        for (FormattedCharSequence line : this.warpLines) {
            widest = Math.max(widest, this.textRenderer.width(line));
        }
        return Math.round(widest * this.scale);
    }

    public void setText(Component text) {
        this.text = text;
        this.rewrap();
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
