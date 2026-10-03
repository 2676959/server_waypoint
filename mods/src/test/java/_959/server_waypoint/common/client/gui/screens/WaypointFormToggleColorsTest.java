package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormToggleColorsTest {
    @Test
    void visibilityToggleFillsLookLikeThePreviousRelease() {
        // The previous release drew a button fill of 53% black and then Local (#04E500) or Global (#005AE5)
        // at 60% on top. These are the pixels of that stack, composed by hand over three panel pixels and
        // rounding after every layer as the framebuffer does.
        int[][] expected = {
                // panel       Local       Global
                {0xFF000000, 0xFF028900, 0xFF003689},
                {0xFF666666, 0xFF169D13, 0xFF13499D},
                {0xFF364A60, 0xFF0C9712, 0xFF0A449B}
        };
        for (int[] row : expected) {
            String panel = String.format("panel #%06X", row[0] & 0xFFFFFF);
            assertPixel(row[1], compositeOver(AbstractWaypointPropertiesScreen.LOCAL_TOGGLE_COLOR, row[0]),
                    "Local over " + panel);
            assertPixel(row[2], compositeOver(AbstractWaypointPropertiesScreen.GLOBAL_TOGGLE_COLOR, row[0]),
                    "Global over " + panel);
        }
    }

    /** Two composited pixels may differ by one in a channel through rounding. */
    private static void assertPixel(int expected, int actual, String what) {
        for (int shift = 16; shift >= 0; shift -= 8) {
            int difference = Math.abs((expected >> shift & 0xFF) - (actual >> shift & 0xFF));
            assertTrue(difference <= 1,
                    () -> String.format("%s: expected #%06X, got #%06X", what, expected & 0xFFFFFF, actual & 0xFFFFFF));
        }
    }

    private static int compositeOver(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255.0D;
        int red = compositeChannel(foreground >> 16 & 0xFF, background >> 16 & 0xFF, alpha);
        int green = compositeChannel(foreground >> 8 & 0xFF, background >> 8 & 0xFF, alpha);
        int blue = compositeChannel(foreground & 0xFF, background & 0xFF, alpha);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int compositeChannel(int foreground, int background, double alpha) {
        return (int) Math.round(foreground * alpha + background * (1.0D - alpha));
    }
}
