//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;

public class IntegerSlider extends ShiftableClickableWidget {
    private static final int padding = 5;
    private final Consumer<Integer> onChange;
    private final IntegerField integerField;
    private final Slider slider;
    private GuiEventListener focused;

    public IntegerSlider(int x, int y, int min, int max, int defaultValue, Consumer<Integer> onChange, Font textRenderer) {
        this(x, y, 100, 30, min, max, defaultValue, onChange, textRenderer);
    }

    public IntegerSlider(int x, int y, int sliderWidth, int fieldWidth, int min, int max, int defaultValue, Consumer<Integer> onChange, Font textRenderer) {
        this(x, y, sliderWidth, fieldWidth, min, max, defaultValue, onChange, textRenderer, 1.0F);
    }

    /** Scales the number field and track height while retaining the requested track width. */
    public IntegerSlider(int x, int y, int sliderWidth, int fieldWidth, int min, int max, int defaultValue,
                         Consumer<Integer> onChange, Font textRenderer, float controlScale) {
        this(x, y, sliderWidth, numberField(x + sliderWidth + padding, y, fieldWidth, min, max, defaultValue,
                textRenderer, controlScale), onChange);
        this.setValue(defaultValue);
    }

    /**
     * A slider around {@code integerField}, which sits right of the track and gives it its range and
     * height, and which typing goes to first. It doesn't set a value; call {@link #setValue}.
     */
    protected IntegerSlider(int x, int y, int sliderWidth, IntegerField integerField, Consumer<Integer> onChange) {
        super(x, y, sliderWidth + integerField.getWidth() + padding, integerField.getVisualHeight(), Component.nullToEmpty("Integer Slider"));
        this.onChange = onChange;
        this.integerField = integerField;
        int min = integerField.minValue;
        this.slider = new Slider(x, y, sliderWidth, this.height, integerField.maxValue - min);

        this.integerField.setValueEnteredCallback(value -> {
            this.slider.setSliderLevelWithNoUpdate(value - min);
            this.onChange.accept(value);
        });

        this.slider.setOnChange(level -> {
            int value = level + min;
            this.integerField.setValue(String.valueOf(value));
            this.onChange.accept(value);
        });
        this.focused = this.integerField;
    }

    private static IntegerField numberField(int x, int y, int width, int min, int max, int defaultValue,
                                             Font textRenderer, float controlScale) {
        IntegerField field = new ScaledIntegerField(x, y, width, min, max, defaultValue, textRenderer, controlScale);
        field.setYOffset(2);
        return field;
    }

    public void updateFocused(GuiEventListener focused) {
        this.focused.setFocused(false);
        this.focused = focused;
        this.focused.setFocused(true);
    }

    public void setValue(int value) {
        this.integerField.setValue(String.valueOf(value));
        this.slider.setSliderLevel(value - this.integerField.minValue);
    }

    private void syncActiveState() {
        this.integerField.active = this.active;
        this.slider.setActive(this.active);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) updateFocused(this.focused);
        else this.focused.setFocused(false);
    }

    /**
     * Whether typing goes to the number field: the slider has focus, and its field rather than its
     * track was selected last.
     */
    public boolean isEditingNumber() {
        return this.integerField.isFocused();
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        int sliderWidth = slider.getWidth();
        slider.setX(x);
        integerField.setX(x + sliderWidth + padding);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        slider.setY(y);
        integerField.setY(y);
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        int shiftedX = this.getShiftedX();
        slider.setX(shiftedX);
        integerField.setX(shiftedX + slider.getWidth() + padding);
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        int shiftedY = this.getShiftedY();
        slider.setY(shiftedY);
        integerField.setY(shiftedY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isActive()) {
            return false;
        }
        if (this.integerField.mouseClicked(mouseX, mouseY, button)) {
            updateFocused(this.integerField);
            return true;
        }
        if (mouseX >= this.slider.getX() && mouseX <= this.slider.getX() + this.slider.getWidth() &&
            mouseY >= this.slider.getY() && mouseY <= this.slider.getY() + this.slider.getHeight()) {
            this.slider.mouseClickedOrDragged(mouseX);
            updateFocused(this.slider);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.isActive()) {
            return false;
        }
        if (this.focused == this.slider) {
            this.slider.mouseClickedOrDragged(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isActive()) {
            return false;
        }
        if (mouseX >= this.slider.getX() && mouseX <= this.slider.getX() + this.slider.getWidth() &&
            mouseY >= this.slider.getY() && mouseY <= this.slider.getY() + this.slider.getHeight()) {
            this.slider.mouseScrolled(verticalAmount);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.isActive() && this.integerField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.isActive() && this.integerField.charTyped(chr, modifiers);
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.syncActiveState();
        this.slider.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.integerField.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.integerField.updateNarration(builder);
    }

    /** The field's geometry and pointer coordinates follow the same transform as its rendering. */
    static class ScaledIntegerField extends IntegerField {
        private float controlScale = 1.0F;
        private boolean nativeCoordinates;

        ScaledIntegerField(int x, int y, int width, int min, int max, int defaultValue, Font font, float controlScale) {
            super(x, y, width, min, max, defaultValue, Component.empty(), font);
            if (!Float.isFinite(controlScale) || controlScale <= 0) {
                throw new IllegalArgumentException("controlScale must be finite and positive");
            }
            this.controlScale = controlScale;
        }

        @Override
        public int getWidth() {
            return this.nativeCoordinates ? super.getWidth() : Math.round(super.getWidth() * this.controlScale);
        }

        @Override
        public int getHeight() {
            return this.nativeCoordinates ? super.getHeight() : Math.round(super.getHeight() * this.controlScale);
        }

        @Override
        public int getVisualWidth() {
            return Math.round(super.getVisualWidth() * this.controlScale);
        }

        @Override
        public int getVisualHeight() {
            return Math.round(super.getVisualHeight() * this.controlScale);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (this.nativeCoordinates) {
                return super.isMouseOver(mouseX, mouseY);
            }
            return this.visible && mouseX >= this.getVisualX() && mouseY >= this.getVisualY()
                    && mouseX < this.getVisualX() + this.getVisualWidth()
                    && mouseY < this.getVisualY() + this.getVisualHeight();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return false;
            }
            this.nativeCoordinates = true;
            try {
                return this.clickUnscaled(this.getVisualX() + (mouseX - this.getVisualX()) / this.controlScale,
                        this.getVisualY() + (mouseY - this.getVisualY()) / this.controlScale, button);
            } finally {
                this.nativeCoordinates = false;
            }
        }

        /** Vanilla click dispatch, separate from the coordinate transform for sound-free probes. */
        protected boolean clickUnscaled(double mouseX, double mouseY, int button) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void
        //$ render_widget_method_swap
        extractWidgetRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int x = this.getVisualX();
            int y = this.getVisualY();
            push(context);
            translate(context, x, y);
            scale(context, this.controlScale, this.controlScale);
            translate(context, -x, -y);
            this.nativeCoordinates = true;
            try {
                super.
                //$ render_widget_method_swap
                extractWidgetRenderState
                        (context, x + Math.round((mouseX - x) / this.controlScale),
                                y + Math.round((mouseY - y) / this.controlScale), deltaTicks);
            } finally {
                this.nativeCoordinates = false;
                pop(context);
            }
        }
    }

    public static class Slider extends AbstractColorBgSlider {
        private Consumer<Integer> onChange;

        public Slider(int x, int y, int width, int height, int maxLevel) {
            super(x, y, width, height, maxLevel);
        }

        public void setOnChange(Consumer<Integer> onChange) {
            this.onChange = onChange;
        }

        public void setSliderLevelWithNoUpdate(int sliderLevel) {
            super.setSliderLevel(sliderLevel);
        }

        @Override
        public void setSliderLevel(int sliderLevel) {
            super.setSliderLevel(sliderLevel);
            if (onChange != null) onChange.accept(sliderLevel);
        }

        @Override
        public void drawSlotBackground(GuiGraphicsExtractor context) {
            drawSolidColor(context, WidgetThemeState.controlBackground(this.isActive(), false));
        }

        @Override
        public void updateSliderCenter(float sliderCenter) {
            super.updateSliderCenter(sliderCenter);
            if (onChange != null) onChange.accept(getSliderLevel());
        }
    }
}
