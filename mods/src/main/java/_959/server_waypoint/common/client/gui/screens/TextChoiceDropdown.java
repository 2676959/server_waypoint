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

import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * A dropdown of text choices that opens downward, or upward after {@code setExpansionDirection(REVERSE)}:
 * the trigger shows {@link #triggerLabel()} and an open/closed arrow, and each {@link TextChoice} shows
 * its label. The trigger and its choices scale together. Labels are clipped inside the outline;
 * the trigger keeps a column on the right for the arrow.
 */
abstract class TextChoiceDropdown extends AbstractDropdownMenuWidget {
    private static final int TEXT_INSET = 2;
    private final ScalableText label;
    private final ScalableText arrow;
    private final int textTop;

    protected TextChoiceDropdown(int width, Component message, Font font) {
        this(width, message, font, 1.0F);
    }

    protected TextChoiceDropdown(int width, Component message, Font font, float textScale) {
        super(0, 0, width, Math.round((font.lineHeight + 2) * textScale), message,
                LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        this.label = new ScalableText(0, 0, Component.empty(), textScale, TEXT_PRIMARY, font);
        this.arrow = this.createDropdownArrow(font, textScale);
        this.textTop = (this.getHeight() - this.label.getHeight()) / 2 + 1;
    }

    /** The width a trigger needs to show a label {@code labelWidth} wide: the text inset and the arrow's column. */
    static int triggerWidth(int labelWidth) {
        return TEXT_INSET + labelWidth + ARROW_COLUMN_WIDTH;
    }

    /** What the trigger shows, read each time it is drawn. */
    protected abstract Component triggerLabel();

    /** Resizes the choices with the trigger, so the popup stays as wide as the control. */
    @Override
    public void setWidth(int width) {
        for (AbstractMenuItem choice : this.getMenuItems()) {
            choice.setWidth(width);
        }
        super.setWidth(width);
    }

    /** The trigger label's visible center, relative to the top of the control. */
    protected final float getLabelAlignmentCenter() {
        return this.textTop + this.label.getVerticalAlignmentCenter();
    }

    @Override
    protected void renderDropdownControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        Component message = this.triggerLabel();
        this.setMessage(message);
        this.renderChoice(context, this, message, false, mouseX, mouseY, deltaTicks);
        this.renderDropdownArrow(context, this.arrow, this.textTop, mouseX, mouseY, deltaTicks);
    }

    private void renderChoice(GuiGraphicsExtractor context, ShiftableClickableWidget widget,
            Component message, boolean popup, int mouseX, int mouseY, float deltaTicks) {
        this.renderTextChoiceSurface(context, widget, popup);
        this.label.setText(message);
        this.label.setColor(widget.active ? TEXT_PRIMARY : TEXT_DISABLED);
        this.label.setPosition(widget.getX() + TEXT_INSET, widget.getY() + this.textTop);
        int labelRight = popup ? Math.min(widget.getWidth(), this.getPopupContentWidth()) - TEXT_INSET
                : widget.getWidth() - ARROW_COLUMN_WIDTH;
        context.enableScissor(widget.getX() + TEXT_INSET, widget.getY() + 1,
                widget.getX() + Math.max(TEXT_INSET, labelRight),
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
