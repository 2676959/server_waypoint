//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;

/** Editable text input with a separately opened list of choices. */
public final class ComboBoxWidget extends AbstractDropdownMenuWidget {
    private final List<String> values;
    private final Component label;
    private final Consumer<String> onValueChanged;
    private final TextInput input;
    private final ScalableText arrow;
    private boolean settingValue;

    public ComboBoxWidget(int x, int y, int width, int height, Component label, Font font,
                          List<String> values, String initialValue, Consumer<String> onValueChanged) {
        super(x, y, width, height, label, LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        this.values = values.stream().distinct().toList();
        this.label = label;
        this.onValueChanged = Objects.requireNonNull(onValueChanged);
        this.input = new TextInput(label, font);
        this.input.setMaxLength(Integer.MAX_VALUE);
        this.input.setResponder(value -> {
            this.closeMenuIfOpen();
            Component message = this.label.copy().append(Component.literal(value));
            this.setMessage(message);
            this.setTooltip(Tooltip.create(message));
            if (!this.settingValue) {
                this.onValueChanged.accept(value);
            }
        });
        this.layoutInput();
        this.arrow = new ScalableText(0, 0, Component.literal("▼"), () -> WidgetThemeState.text(this.active), font);
        for (String option : this.values) {
            this.addMenuItem(new TextMenuItem(option, width, height, font));
        }
        this.setValue(initialValue);
    }

    public String getValue() {
        return this.input.getValue();
    }

    /** Sets arbitrary text without invoking the user-change callback. */
    public void setValue(String value) {
        this.settingValue = true;
        try {
            this.input.setValue(Objects.requireNonNull(value));
        } finally {
            this.settingValue = false;
        }
    }

    @Override
    protected int getSelectedMenuItemIndex() {
        return this.values.indexOf(this.getValue());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isActive() || button != 0) {
            return false;
        }
        if (mouseX >= this.getX() && mouseX < this.getX() + this.width - 14
                && mouseY >= this.getY() && mouseY < this.getY() + this.height) {
            this.closeMenuIfOpen();
            this.setFocused(true);
            this.input.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isActive() || !this.isFocused()) {
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
                || this.input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.isActive() && this.isFocused() && this.input.charTyped(chr, modifiers);
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.layoutInput();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.layoutInput();
    }

    @Override
    public void setXOffset(int offset) {
        super.setXOffset(offset);
        this.layoutInput();
    }

    @Override
    public void setYOffset(int offset) {
        super.setYOffset(offset);
        this.layoutInput();
    }

    private void layoutInput() {
        if (this.input == null) {
            return;
        }
        this.input.setPosition(this.getX() + 3, this.getY() + (this.height - this.input.textHeight) / 2);
        this.input.setWidth(Math.max(1, this.width - 20));
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (this.input != null) {
            this.input.setFocused(focused);
        }
        if (!focused) {
            this.closeMenuIfOpen();
        }
    }

    @Override
    public void setWidth(int width) {
        for (AbstractMenuItem item : this.getMenuItems()) {
            item.setWidth(width);
        }
        super.setWidth(width);
        this.layoutInput();
    }

    @Override
    public void setHeight(int height) {
        for (AbstractMenuItem item : this.getMenuItems()) {
            ((TextMenuItem) item).resizeHeight(height);
        }
        super.setHeight(height);
        this.layoutInput();
    }

    @Override
    protected void renderDropdownControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        drawSurface(context, this, false);
        this.input.active = this.active;
        this.input.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.arrow.setText(this.isExpanded() ? "▲" : "▼");
        renderLabel(context, this.arrow, this.getX() + this.width - 12, this.getY(),
                9, this.height, mouseX, mouseY, deltaTicks);
    }

    private static void drawSurface(GuiGraphicsExtractor context, ShiftableClickableWidget widget, boolean popup) {
        int color = popup && !widget.isHovered() && !widget.isFocused()
                ? WidgetThemeManager.getColor(WidgetThemeVariable.POPUP_BACKGROUND)
                : WidgetThemeState.controlBackground(widget.active, widget.isHovered() || widget.isFocused());
        context.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        renderOutline(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                WidgetThemeState.border(widget.active, widget.isFocused(), widget.isHovered()));
    }

    private static void renderLabel(GuiGraphicsExtractor context, ScalableText text, int x, int y,
                                    int width, int height, int mouseX, int mouseY, float deltaTicks) {
        text.setPosition(x, y + (height - text.getHeight()) / 2);
        // Keep a fixed-height control; ScalableText owns text drawing, the control clips long values.
        context.enableScissor(x, y + 1, x + width, y + height - 1);
        text.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        context.disableScissor();
    }

    /** The composite owns the surface; reuse the text field's editing and text renderer. */
    private static final class TextInput extends TranslucentTextField {
        private final int textHeight;

        private TextInput(Component label, Font font) {
            super(0, 0, 1, label, font);
            this.textHeight = font.lineHeight;
        }

        @Override
        public void
        //$ render_widget_method_swap
        extractWidgetRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            this.updateThemeTextColors();
            this.renderTextField(context, mouseX, mouseY, deltaTicks);
        }
    }

    private final class TextMenuItem extends AbstractMenuItem {
        private final String option;
        private final ScalableText text;

        private TextMenuItem(String option, int width, int height, Font font) {
            super(width, height, Component.literal(option));
            this.option = option;
            this.text = new ScalableText(0, 0, this.getMessage(), () -> WidgetThemeState.text(this.active), font);
            this.setTooltip(Tooltip.create(this.getMessage()));
        }

        private void resizeHeight(int height) {
            this.height = height;
        }

        @Override
        protected void onSelected() {
            ComboBoxWidget.this.setValue(this.option);
            ComboBoxWidget.this.onValueChanged.accept(this.option);
        }

        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            drawSurface(context, this, true);
            renderLabel(context, this.text, this.getX() + 3, this.getY(),
                    Math.max(0, this.width - 6), this.height, mouseX, mouseY, deltaTicks);
        }
    }
}
