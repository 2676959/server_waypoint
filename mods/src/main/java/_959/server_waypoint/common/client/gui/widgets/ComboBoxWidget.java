//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutlineWithoutTop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;

/** Editable text input with a separately opened list of choices. */
public final class ComboBoxWidget extends AbstractDropdownMenuWidget {
    private static final int TEXT_INSET = SuggestingTextInput.OUTLINE_PADDING;
    private List<String> values = List.of();
    private final Component label;
    private final Consumer<String> onValueChanged;
    private final TextInput input;
    private final ScalableText arrow;
    private final Font font;
    private boolean settingValue;

    public ComboBoxWidget(int x, int y, int width, Component label, Font font,
                          List<String> values, String initialValue, Consumer<String> onValueChanged) {
        super(x, y, width, font.lineHeight + 2, label, LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        this.label = label;
        this.font = font;
        this.onValueChanged = Objects.requireNonNull(onValueChanged);
        this.input = new TextInput(label, font);
        this.input.setMaxLength(Integer.MAX_VALUE);
        this.input.setSuggestionsProvider(() -> this.values);
        this.input.setResponder(value -> {
            this.closeMenuIfOpen();
            Component message = this.label.copy().append(Component.literal(value));
            this.setMessage(message);
            if (!this.settingValue) {
                this.onValueChanged.accept(value);
            }
        });
        this.setY(y);
        this.arrow = new ScalableText(0, 0, Component.literal("⏷"), () -> WidgetThemeState.text(this.active), font);
        this.setValues(values);
        this.setValue(initialValue);
    }

    /** Replaces popup choices without changing the current text or invoking its callback. */
    public void setValues(List<String> values) {
        this.values = Objects.requireNonNull(values).stream().distinct().toList();
        this.clearMenuItems();
        for (String option : this.values) {
            this.addMenuItem(new TextMenuItem(option, this.width, this.height, this.font));
        }
        this.input.refreshSuggestions();
    }

    /** Overrides the suggestion source; popup choices remain independently configurable. */
    public void setSuggestionsProvider(Supplier<List<String>> provider) {
        this.input.setSuggestionsProvider(provider);
    }

    public boolean closeSuggestionsIfOpen() {
        return this.input.closeSuggestionsIfOpen();
    }

    @Override
    protected void onExpandedChanged(boolean expanded) {
        if (this.input != null) {
            this.input.setSuggestionsEnabled(!expanded);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || (this.isActive() && this.input != null
                && this.input.isMouseOverSuggestion(mouseX, mouseY));
    }

    @Override
    public void renderPopup(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.renderPopup(context, mouseX, mouseY, deltaTicks);
        if (!this.isExpanded() && this.visible && this.active) {
            nextLayer(context);
            try {
                this.input.renderSuggestions(context, mouseX, mouseY);
            } finally {
                previousLayer(context);
            }
        }
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
        if (!this.isActive() || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.input.mouseClickedSuggestion(mouseX, mouseY)) {
            return true;
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
        super.setX(x - TEXT_INSET);
        this.layoutInput();
    }

    @Override
    public void setY(int y) {
        // Match standalone text inputs: callers position the text, not the surrounding border.
        super.setY(y - TEXT_INSET);
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
        this.input.setPosition(this.getX() + TEXT_INSET, this.getY() + TEXT_INSET);
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
        this.arrow.setText(this.isExpanded() ? "⏶" : "⏷");
        renderLabel(context, this.arrow, this.getX() + this.width - 12, this.getY(),
                9, this.height, mouseX, mouseY, deltaTicks);
    }

    private static void drawSurface(GuiGraphicsExtractor context, ShiftableClickableWidget widget, boolean popup) {
        int color = popup && !widget.isHovered() && !widget.isFocused()
                ? WidgetThemeManager.getColor(WidgetThemeVariable.POPUP_BACKGROUND)
                : WidgetThemeState.controlBackground(widget.active, widget.isHovered() || widget.isFocused());
        int x = widget.getX();
        int y = widget.getY();
        int right = x + widget.getWidth();
        int bottom = y + widget.getHeight();
        int border = WidgetThemeState.border(widget.active, widget.isFocused(), widget.isHovered());
        context.fill(x, y, right, bottom, color);
        if (popup) {
            // The preceding control/row owns the shared horizontal border.
            renderOutlineWithoutTop(context, x, y, widget.getWidth(), widget.getHeight(), border);
        } else {
            renderOutline(context, x, y, widget.getWidth(), widget.getHeight(), border);
        }
    }

    private static void renderLabel(GuiGraphicsExtractor context, ScalableText text, int x, int y,
                                    int width, int height, int mouseX, int mouseY, float deltaTicks) {
        text.setPosition(x, y + TEXT_INSET);
        // Keep a fixed-height control; ScalableText owns text drawing, the control clips long values.
        context.enableScissor(x, y + 1, x + width, y + height - 1);
        text.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        context.disableScissor();
    }

    /** The composite owns the surface; reuse the shared input's editing, completion, and text renderer. */
    private final class TextInput extends SuggestingTextInput {
        private TextInput(Component label, Font font) {
            super(0, 0, 1, label, font);
        }

        @Override
        protected int getSuggestionsX() {
            return ComboBoxWidget.this.getX();
        }

        @Override
        protected int getSuggestionsWidth(int maxTextWidth) {
            return ComboBoxWidget.this.getWidth();
        }

        @Override
        protected int getSuggestionsY() {
            return ComboBoxWidget.this.getY() + ComboBoxWidget.this.getHeight();
        }

    }

    private final class TextMenuItem extends AbstractMenuItem {
        private final String option;
        private final ScalableText text;

        private TextMenuItem(String option, int width, int height, Font font) {
            super(width, height, Component.literal(option));
            this.option = option;
            this.text = new ScalableText(0, 0, this.getMessage(), () -> WidgetThemeState.text(this.active), font);
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
            renderLabel(context, this.text, this.getX() + TEXT_INSET, this.getY(),
                    Math.max(0, this.width - 2 * TEXT_INSET), this.height, mouseX, mouseY, deltaTicks);
        }
    }
}
