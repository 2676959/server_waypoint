package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class RGBColorPicker extends Abstract3ChannelColorPicker<RGBColorPicker.RGBChannelSlider> {
    public RGBColorPicker(int x, int y, int slotWidth, int slotHeight, ColorPickerCallBack callback) {
        super(x, y, slotWidth, slotHeight, Text.of("RGB Color"),
                new RGBChannelSlider(0, 0, slotWidth, slotHeight, 0xFF000000, 0xFFFF0000),
                new RGBChannelSlider(0, 0, slotWidth, slotHeight, 0xFF000000, 0xFF00FF00),
                new RGBChannelSlider(0, 0, slotWidth, slotHeight, 0xFF000000, 0xFF0000FF),
                callback);
    }

    @Override
    public void onChannel0Update() {
        int color = getColor();
        this.slider1.setStartColor(color & 0xFFFF00FF);
        this.slider1.setEndColor(color | 0xFF00FF00);
        this.slider2.setStartColor(color & 0xFFFFFF00);
        this.slider2.setEndColor(color | 0xFF0000FF);
    }

    @Override
    public void onChannel1Update() {
        int color = getColor();
        this.slider0.setStartColor(color & 0xFF00FFFF);
        this.slider0.setEndColor(color | 0xFFFF0000);
        this.slider2.setStartColor(color & 0xFFFFFF00);
        this.slider2.setEndColor(color | 0xFF0000FF);
    }

    @Override
    public void onChannel2Update() {
        int color = getColor();
        this.slider0.setStartColor(color & 0xFF00FFFF);
        this.slider0.setEndColor(color | 0xFFFF0000);
        this.slider1.setStartColor(color & 0xFFFF00FF);
        this.slider1.setEndColor(color | 0xFF00FF00);
    }

    @Override
    public int getColor() {
        return 0xFF000000 | slider0.getSliderLevel() << 16 | slider1.getSliderLevel() << 8 | slider2.getSliderLevel();
    }

    /**
     * set color and slider position
     * */
    @Override
    public void setColor(int rgb) {
        this.slider0.setStartColor(rgb & 0xFF00FFFF);
        this.slider0.setEndColor(rgb | 0xFFFF0000);
        this.slider1.setStartColor(rgb & 0xFFFF00FF);
        this.slider1.setEndColor(rgb | 0xFF00FF00);
        this.slider2.setStartColor(rgb & 0xFFFFFF00);
        this.slider2.setEndColor(rgb | 0xFF0000FF);
        this.slider0.setSliderLevel((rgb & 0x00FF0000) >> 16);
        this.slider1.setSliderLevel((rgb & 0x0000FF00) >> 8);
        this.slider2.setSliderLevel((rgb & 0x000000FF));
    }

    public static class RGBChannelSlider extends AbstractColorBgSlider implements Drawable {
        public RGBChannelSlider(int x, int y, int width, int height, int startColor, int endColor) {
            super(x, y, width, height, 255);
            this.setStartColor(startColor);
            this.setEndColor(endColor);
        }

        @Override
        public void drawSlotBackground(VertexConsumer vertexConsumer, Matrix4f matrix) {
            drawGradient(vertexConsumer, matrix, this.startColor, this.endColor);
        }
    }
}
