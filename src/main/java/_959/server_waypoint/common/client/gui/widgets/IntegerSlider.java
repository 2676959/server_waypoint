package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.WidgetThemeColors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class IntegerSlider extends ShiftableClickableWidget {
    private static final int padding = 5;
    private final Consumer<Integer> onChange;
    private final IntegerField integerField;
    private final Slider slider;
    private Element focused;

    public IntegerSlider(int x, int y, int min, int max, int defaultValue, Consumer<Integer> onChange, TextRenderer textRenderer) {
        this(x, y, 100, 30, min, max, defaultValue, onChange, textRenderer);
    }

    public IntegerSlider(int x, int y, int sliderWidth, int fieldWidth, int min, int max, int defaultValue, Consumer<Integer> onChange, TextRenderer textRenderer) {
        super(x, y, sliderWidth + fieldWidth + padding, 0, Text.of("Integer Slider"));
        this.onChange = onChange;
        this.integerField = new IntegerField(x + sliderWidth + padding, y, fieldWidth, min, max, defaultValue, Text.empty(), textRenderer);
        this.integerField.setYOffset(2);
        this.slider = new Slider(x, y, sliderWidth, this.integerField.getVisualHeight(), max - min);
        this.height = this.integerField.getVisualHeight();

        this.slider.setSliderLevel(defaultValue - min);

        this.integerField.setValueEnteredCallback(value -> {
            this.slider.setSliderLevelWithNoUpdate(value - min);
            this.onChange.accept(value);
        });

        this.slider.setOnChange(level -> {
            int value = level + min;
            this.integerField.setText(String.valueOf(value));
            this.onChange.accept(value);
        });
        this.focused = this.integerField;
        this.setValue(defaultValue);
    }

    public void updateFocused(Element focused) {
        this.focused.setFocused(false);
        this.focused = focused;
        this.focused.setFocused(true);
    }

    public void setValue(int value) {
        this.integerField.setText(String.valueOf(value));
        this.slider.setSliderLevel(value);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) updateFocused(this.focused);
        else this.focused.setFocused(false);
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
        if (this.focused == this.slider) {
            this.slider.mouseClickedOrDragged(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= this.slider.getX() && mouseX <= this.slider.getX() + this.slider.getWidth() &&
            mouseY >= this.slider.getY() && mouseY <= this.slider.getY() + this.slider.getHeight()) {
            this.slider.mouseScrolled(verticalAmount);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.integerField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.integerField.charTyped(chr, modifiers);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.slider.render(context, mouseX, mouseY, deltaTicks);
        this.integerField.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.integerField.appendNarrations(builder);
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
        public void drawSlotBackground(VertexConsumer vertexConsumer, Matrix4f matrix) {
            drawSolidColor(vertexConsumer, matrix, WidgetThemeColors.TRANSPARENT_BG_COLOR);
        }

        @Override
        public void updateSliderCenter(float sliderCenter) {
            super.updateSliderCenter(sliderCenter);
            if (onChange != null) onChange.accept(getSliderLevel());
        }
    }
}
