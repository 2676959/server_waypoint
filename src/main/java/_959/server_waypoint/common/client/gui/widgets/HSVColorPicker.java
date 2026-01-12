package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import static _959.server_waypoint.util.ColorUtils.*;

public class HSVColorPicker extends Abstract3ChannelColorPicker<HSVColorPicker.HSVSlider> {
    public HSVColorPicker(int x, int y, int slotWidth, int slotHeight, ColorPickerCallBack callback) {
        super(x, y, slotWidth, slotHeight, Text.of("HSV Color"),
                new HueSlider(0, 0, slotWidth, slotHeight),
                new SaturationSlider(0, slotHeight, slotWidth, slotHeight, 0xFFFFFFFF),
                new BrightnessSlider(0, 2* slotHeight, slotWidth, slotHeight, 0xFFFFFFFF),
                callback);
    }

    @Override
    public int getColor() {
        return HSVtoRGB(this.slider0.getSliderLevel(), this.slider1.getSliderLevel(), this.slider2.getSliderLevel());
    }

    @Override
    public void setColor(int color) {
        int[] hsvData = RGBtoHSV(color);
        int h = hsvData[0];
        int s = hsvData[1];
        int v = hsvData[2];
        int hueColor = hsvData[3];

        // hue strip is influenced by both saturation and brightness
        this.slider0.setVisualSaturation(s);
        this.slider0.setVisualBrightness(v);

        // brightness only influences the saturation strip
        this.slider1.setVisualBrightness(v);

        // saturation only influences the brightness strip
        this.slider2.setVisualSaturation(s);

        // set display color and slider position
        this.slider1.setBaseColor(hueColor);
        this.slider2.setBaseColor(hueColor);
        this.slider0.setSliderLevel(h);
        this.slider1.setSliderLevel(s);
        this.slider2.setSliderLevel(v);
    }

    @Override
    public void onChannel0Update() {
        // Hue component
        int hueColor = getPureHue(this.slider0.getSliderLevel());
        this.slider1.setBaseColor(hueColor);
        this.slider2.setBaseColor(hueColor);
    }

    @Override
    public void onChannel1Update() {
        // Saturation component
        int saturation = this.slider1.getSliderLevel();
        this.slider0.setVisualSaturation(saturation);
        this.slider2.setVisualSaturation(saturation);
    }

    @Override
    public void onChannel2Update() {
        // Brightness component
        int brightness = this.slider2.getSliderLevel();
        this.slider0.setVisualBrightness(brightness);
        this.slider1.setVisualBrightness(brightness);
    }

    public static abstract class HSVSlider extends AbstractGradientSlider {
        private int whiteOverlay = 0x00FFFFFF;
        private int blackOverlay = 0x00000000;

        public HSVSlider(int x, int y, int slotWidth, int slotHeight, int sliderWidth, int maxLevel) {
            super(x, y, slotWidth, slotHeight, sliderWidth, maxLevel);
        }

        public HSVSlider(int x, int y, int slotWidth, int slotHeight, int maxLevel) {
            super(x, y, slotWidth, slotHeight, maxLevel);
        }

        public void setBaseColor(int color) {
            setEndColor(color);
        }

        void setVisualSaturation(int saturation) {
            this.whiteOverlay = ((100 - saturation) * 255 / 100) << 24 | 0x00FFFFFF;
        };

        void setVisualBrightness(int brightness) {
            this.blackOverlay = ((100 - brightness) * 255 / 100) << 24 & 0xFF000000;
        };

        public void drawWhiteOverlay(VertexConsumer vertexConsumer, Matrix4f matrix) {
            drawGradient(vertexConsumer, matrix, this.x, this.endX, this.whiteOverlay, this.whiteOverlay);
        }

        public void drawBlackOverlay(VertexConsumer vertexConsumer, Matrix4f matrix) {
            drawGradient(vertexConsumer, matrix, this.x, this.endX, this.blackOverlay, this.blackOverlay);
        }
    }

    public static class HueSlider extends HSVSlider {
        private final float quadWidth;
        private final float secondQuad;
        private final float thirdQuad;
        private final float fourthQuad;
        private final float fifthQuad;
        private final float sixthQuad;

        public HueSlider(int x, int y, int width, int height) {
            super(x, y, width, height, 360);
            this.quadWidth = width / 6F;
            this.secondQuad = x + quadWidth;
            this.thirdQuad = secondQuad + quadWidth;
            this.fourthQuad = thirdQuad + quadWidth;
            this.fifthQuad = fourthQuad + quadWidth;
            this.sixthQuad = fifthQuad + quadWidth;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            MatrixStack matrixStack = context.getMatrices();
            Matrix4f matrix =  matrixStack.peek().getPositionMatrix();
            context.draw(vertexConsumerProvider -> {
                VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getGui());
                drawGradient(vertexConsumer, matrix, this.x, this.secondQuad, RED, YELLOW);
                drawGradient(vertexConsumer, matrix, this.secondQuad, this.thirdQuad, YELLOW, GREEN);
                drawGradient(vertexConsumer, matrix, this.thirdQuad, this.fourthQuad, GREEN, CYAN);
                drawGradient(vertexConsumer, matrix, this.fourthQuad, this.fifthQuad, CYAN, BLUE);
                drawGradient(vertexConsumer, matrix, this.fifthQuad, this.sixthQuad, BLUE, MAGENTA);
                drawGradient(vertexConsumer, matrix, this.sixthQuad, this.endX, MAGENTA, RED);
                drawWhiteOverlay(vertexConsumer, matrix);
                drawBlackOverlay(vertexConsumer, matrix);
                drawSlider(vertexConsumer, matrix);
            });
        }
    }

    public static class SaturationSlider extends HSVSlider {

        public SaturationSlider(int x, int y, int width, int height, int color) {
            super(x, y, width, height, 100);
            this.endColor = color;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            MatrixStack matrixStack = context.getMatrices();
            Matrix4f matrix =  matrixStack.peek().getPositionMatrix();
            context.draw(vertexConsumerProvider -> {
                VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getGui());
                // draw the gradient slot
                drawGradient(vertexConsumer, matrix, this.x, this.endX, 0xFFFFFFFF, endColor);
                drawBlackOverlay(vertexConsumer, matrix);
                // draw the slider
                drawSlider(vertexConsumer, matrix);
            });
        }
    }

    public static class BrightnessSlider extends HSVSlider {
        public BrightnessSlider(int x, int y, int width, int height, int color) {
            super(x, y, width, height, 100);
            this.endColor = color;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            MatrixStack matrixStack = context.getMatrices();
            Matrix4f matrix =  matrixStack.peek().getPositionMatrix();
            context.draw(vertexConsumerProvider -> {
                VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getGui());
                // draw the gradient slot
                drawGradient(vertexConsumer, matrix, this.x, this.endX, endColor, endColor);
                drawWhiteOverlay(vertexConsumer, matrix);
                drawGradient(vertexConsumer, matrix, this.x, this.endX, 0xFF000000, 0);
                // draw the slider
                drawSlider(vertexConsumer, matrix);
            });
        }
    }
}
