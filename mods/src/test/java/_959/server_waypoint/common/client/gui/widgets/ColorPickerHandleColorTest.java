package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each gradient of a color picker shows the picker's current color at its handle, so the handles are
 * drawn in black or white, whichever contrasts more with that color.
 */
class ColorPickerHandleColorTest {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void handlesContrastWithAMidGrayInsteadOfInvertingIt() {
        // The inverse of #808080 is #7F7F7F, which can't be seen on it.
        RGBColorPicker rgb = new RGBColorPicker(0, 0, 120, 11, color -> {});
        HSVColorPicker hsv = new HSVColorPicker(0, 0, 120, 11, color -> {});

        rgb.setColor(0xFF808080);
        hsv.setColor(0xFF808080);

        assertHandles(BLACK, rgb);
        assertHandles(BLACK, hsv);
    }

    @Test
    void handlesAreOpaqueForAColorWithoutAnAlphaByte() {
        RGBColorPicker rgb = new RGBColorPicker(0, 0, 120, 11, color -> {});
        HSVColorPicker hsv = new HSVColorPicker(0, 0, 120, 11, color -> {});

        rgb.setColor(0x336699);
        hsv.setColor(0x336699);

        assertHandles(WHITE, rgb);
        assertHandles(WHITE, hsv);
    }

    @Test
    void handlesFollowClicksAndNumberEntries() {
        List<Integer> colors = new ArrayList<>();
        RGBColorPicker rgb = new RGBColorPicker(0, 0, 120, 11, colors::add);
        rgb.setColor(0xFFFFFFFF);
        assertHandles(BLACK, rgb);

        // The red slider's top row, then the green slider's second row, both at their left end.
        rgb.mouseClicked(0, 5, 0);
        assertHandles(BLACK, rgb);
        rgb.mouseClicked(0, 16, 0);
        assertHandles(WHITE, rgb);
        assertEquals(List.of(0xFF00FFFF, 0xFF0000FF), colors);

        rgb.updateSlider1(255);
        assertHandles(BLACK, rgb);
    }

    @Test
    void handlesFollowTheHsvEntries() {
        HSVColorPicker hsv = new HSVColorPicker(0, 0, 120, 11, color -> {});
        hsv.setColor(0xFF00AAAA);
        assertHandles(BLACK, hsv);

        hsv.updateSlider2(10);
        assertHandles(WHITE, hsv);
    }

    @Test
    void aDeactivatedSliderKeepsTheDisabledThemeHandle() {
        WidgetThemeManager.setColor(WidgetThemeVariable.SLIDER_THUMB_DISABLED, 0xFF123456);
        RGBColorPicker rgb = new RGBColorPicker(0, 0, 120, 11, color -> {});
        rgb.setColor(0xFF808080);

        rgb.slider0.setActive(false);

        assertEquals(0xFF123456, rgb.slider0.getHandleColor());
        assertEquals(BLACK, rgb.slider1.getHandleColor());
    }

    @Test
    void aSliderOutsideAPickerDrawsTheThemedHandle() {
        WidgetThemeManager.setColor(WidgetThemeVariable.ACCENT, 0xFF654321);

        RGBColorPicker.RGBChannelSlider slider = new RGBColorPicker.RGBChannelSlider(0, 0, 120, 11, BLACK, 0xFFFF0000);

        assertEquals(0xFF654321, slider.getHandleColor());
    }

    private static void assertHandles(int expected, Abstract3ChannelColorPicker<?> picker) {
        assertEquals(expected, picker.slider0.getHandleColor());
        assertEquals(expected, picker.slider1.getHandleColor());
        assertEquals(expected, picker.slider2.getHandleColor());
    }
}
