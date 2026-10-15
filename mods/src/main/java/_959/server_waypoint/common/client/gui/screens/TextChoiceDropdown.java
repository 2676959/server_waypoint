//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.ShiftableClickableWidget;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutlineWithoutBottom;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutlineWithoutTop;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.FOCUS_RING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.POPUP_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * A dropdown of text choices that opens downward, or upward after {@code setExpansionDirection(REVERSE)}:
 * the trigger shows {@link #triggerLabel()} and an open/closed arrow, and each {@link TextChoice} shows
 * its label. The trigger and its choices are a line of text plus 2 pixels high, with the text 2 pixels
 * in, like text fields and comboboxes. Labels are clipped inside the outline; the trigger keeps a
 * column on the right for the arrow.
 */
abstract class TextChoiceDropdown extends AbstractDropdownMenuWidget {
    private static final int TEXT_INSET = 2;
    private static final int ARROW_COLUMN = 14;
    private static final int ARROW_X = 12;
    private final ScalableText label;
    private final ScalableText arrow;

    protected TextChoiceDropdown(int width, Component message, Font font) {
        super(0, 0, width, font.lineHeight + 2, message,
                LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        this.label = new ScalableText(0, 0, Component.empty(), TEXT_PRIMARY, font);
        this.arrow = new ScalableText(0, 0, Component.literal("⏷"), TEXT_PRIMARY, font);
    }

    /** The width a trigger needs to show a label {@code labelWidth} wide: the text inset and the arrow's column. */
    static int triggerWidth(int labelWidth) {
        return TEXT_INSET + labelWidth + ARROW_COLUMN;
    }

    /** What the trigger shows, read each time it is drawn. */
    protected abstract Component triggerLabel();

    @Override
    protected void renderDropdownControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        Component message = this.triggerLabel();
        this.setMessage(message);
        this.renderChoice(context, this, message, false, mouseX, mouseY, deltaTicks);
        this.arrow.setText(this.isExpanded() ? "⏶" : "⏷");
        this.arrow.setColor(this.active ? TEXT_PRIMARY : TEXT_DISABLED);
        this.arrow.setPosition(this.getX() + this.getWidth() - ARROW_X, this.getY() + TEXT_INSET);
        this.arrow.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    /**
     * Draws the trigger or a choice: the popup fill, the hover fill, the outline, and the label clipped
     * inside it. A choice in the popup leaves out the edge it shares with the row or trigger before it:
     * its top when the popup opens downward, its bottom when it opens upward.
     */
    private void renderChoice(GuiGraphicsExtractor context, ShiftableClickableWidget widget,
            Component message, boolean popup, int mouseX, int mouseY, float deltaTicks) {
        context.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(),
                widget.getY() + widget.getHeight(), getColor(POPUP_BACKGROUND));
        if (widget.isHovered() || widget.isFocused()) {
            context.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(),
                    widget.getY() + widget.getHeight(), getColor(ROW_HOVER_BACKGROUND));
        }
        int border = getColor(widget.isFocused() ? FOCUS_RING : BORDER);
        if (!popup) {
            renderOutline(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), border);
        } else if (this.getExpansionDirection() == LayoutFlow.Direction.REVERSE) {
            renderOutlineWithoutBottom(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), border);
        } else {
            renderOutlineWithoutTop(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), border);
        }
        this.label.setText(message);
        this.label.setColor(widget.active ? TEXT_PRIMARY : TEXT_DISABLED);
        this.label.setPosition(widget.getX() + TEXT_INSET, widget.getY() + TEXT_INSET);
        context.enableScissor(widget.getX() + TEXT_INSET, widget.getY() + 1,
                widget.getX() + widget.getWidth() - (popup ? TEXT_INSET : ARROW_COLUMN),
                widget.getY() + widget.getHeight() - 1);
        this.label.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        context.disableScissor();
    }

    /** A choice with a label, as wide and as high as the trigger, that runs {@code onSelected} when chosen. */
    protected class TextChoice extends AbstractMenuItem {
        private final Runnable onSelected;

        protected TextChoice(Component label, Runnable onSelected) {
            super(TextChoiceDropdown.this.getWidth(), TextChoiceDropdown.this.getHeight(), label);
            this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
        }

        @Override
        protected void onSelected() {
            this.onSelected.run();
        }

        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            TextChoiceDropdown.this.renderChoice(context, this, this.getMessage(), true, mouseX, mouseY, deltaTicks);
        }
    }
}
