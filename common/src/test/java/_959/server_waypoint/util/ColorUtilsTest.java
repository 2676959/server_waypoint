package _959.server_waypoint.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorUtilsTest {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    void contrastColorIsBlackOverLightColorsAndWhiteOverDarkOnes() {
        assertEquals(WHITE, ColorUtils.getContrastColor(0x000000));
        assertEquals(WHITE, ColorUtils.getContrastColor(0x0000AA));
        assertEquals(BLACK, ColorUtils.getContrastColor(0xFFFF55));
        assertEquals(BLACK, ColorUtils.getContrastColor(0xFFFFFF));
    }

    @Test
    void contrastColorIgnoresTheAlphaByte() {
        assertEquals(ColorUtils.getContrastColor(0xFF336699), ColorUtils.getContrastColor(0x336699));
        assertEquals(ColorUtils.getContrastColor(0xFF336699), ColorUtils.getContrastColor(0x00336699));
        assertEquals(WHITE, ColorUtils.getContrastColor(0x00336699));
    }

    @Test
    void contrastColorDoesNotFavorWhiteLikeTheTextColorDoes() {
        // #808080 is 5.3:1 against black but only 3.9:1 against white.
        assertEquals(BLACK, ColorUtils.getContrastColor(0x808080));
        assertEquals(WHITE, ColorUtils.getSafeTextColor(0x808080));
    }

    @Test
    void contrastColorStaysReadableAgainstEveryColor() {
        double worst = Double.MAX_VALUE;
        for (int r = 0; r < 256; r += 5) {
            for (int g = 0; g < 256; g += 5) {
                for (int b = 0; b < 256; b += 5) {
                    int rgb = r << 16 | g << 8 | b;
                    worst = Math.min(worst, contrast(rgb, ColorUtils.getContrastColor(rgb)));
                }
            }
        }

        assertTrue(worst >= 4.5, "worst contrast " + worst);
    }

    private static double contrast(int rgb1, int rgb2) {
        double luminance1 = luminance(rgb1);
        double luminance2 = luminance(rgb2);
        return (Math.max(luminance1, luminance2) + 0.05) / (Math.min(luminance1, luminance2) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
