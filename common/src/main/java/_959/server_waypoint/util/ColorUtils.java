package _959.server_waypoint.util;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ColorUtils {
    public static final int RED     = 0xFFFF0000; // Hue = 0
    public static final int YELLOW  = 0xFFFFFF00; // Hue = 60
    public static final int GREEN   = 0xFF00FF00; // Hue = 120
    public static final int CYAN    = 0xFF00FFFF; // Hue = 180
    public static final int BLUE    = 0xFF0000FF; // Hue = 240
    public static final int MAGENTA = 0xFFFF00FF; // Hue = 300

    public static final int[] VANILLA_COLORS = new int[] {
            0,
            170,
            43520,
            43690,
            11141120,
            11141290,
            16755200,
            11184810,
            5592405,
            5592575,
            5635925,
            5636095,
            16733525,
            16733695,
            16777045,
            16777215,
    };

    public static final String[] VANILLA_COLOR_NAMES = new String[] {
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white"
    };

    public static final String[] VANILLA_COLOR_CODES = new String[] {
            "#000000",
            "#0000AA",
            "#00AA00",
            "#00AAAA",
            "#AA0000",
            "#AA00AA",
            "#FFAA00",
            "#AAAAAA",
            "#555555",
            "#5555FF",
            "#55FF55",
            "#55FFFF",
            "#FF5555",
            "#FF55FF",
            "#FFFF55",
            "#FFFFFF",
    };

    public static int colorNameToRgb(String colorName) {
        return switch (colorName) {
            case "black" -> 0;
            case "dark_blue" -> 170;
            case "dark_green" -> 43520;
            case "dark_aqua" -> 43690;
            case "dark_red" -> 11141120;
            case "dark_purple" -> 11141290;
            case "gold" -> 16755200;
            case "gray" -> 11184810;
            case "dark_gray" -> 5592405;
            case "blue" -> 5592575;
            case "green" -> 5635925;
            case "aqua" -> 5636095;
            case "red" -> 16733525;
            case "light_purple" -> 16733695;
            case "yellow" -> 16777045;
            case "white" -> 16777215;
            default -> -1;
        };
    }

    @Nullable
    public static String rgbToColorName(int rgb) {
        return switch (rgb) {
            case 0 -> "black";
            case 170 -> "dark_blue";
            case 43520 -> "dark_green";
            case 43690 -> "dark_aqua";
            case 11141120 -> "dark_red";
            case 11141290 -> "dark_purple";
            case 16755200 -> "gold";
            case 11184810 -> "gray";
            case 5592405 -> "dark_gray";
            case 5592575 -> "blue";
            case 5635925 ->  "green";
            case 5636095 ->  "aqua";
            case 16733525 ->  "red";
            case 16733695 ->  "light_purple";
            case 16777045 ->  "yellow";
            case 16777215 ->  "white";
            default -> null;
        };
    }

    public static int colorIndexToRgb(int colorIdx) {
        return switch (colorIdx) {
            case 0 -> 0;
            case 1 -> 170;
            case 2 -> 43520;
            case 3 -> 43690;
            case 4 -> 11141120;
            case 5 -> 11141290;
            case 6 -> 16755200;
            case 7 -> 11184810;
            case 8 -> 5592405;
            case 9 -> 5592575;
            case 10 -> 5635925;
            case 11 -> 5636095;
            case 12 -> 16733525;
            case 13 -> 16733695;
            case 14 -> 16777045;
            default -> 16777215;
        };
    }

    public static int rgbToColorIndex(int rgb) {
        return switch (rgb) {
            case 0 -> 0;
            case 170 -> 1;
            case 43520 -> 2;
            case 43690 -> 3;
            case 11141120 -> 4;
            case 11141290 -> 5;
            case 16755200 -> 6;
            case 11184810 -> 7;
            case 5592405 -> 8;
            case 5592575 -> 9;
            case 5635925 -> 10;
            case 5636095 -> 11;
            case 16733525 -> 12;
            case 16733695 -> 13;
            case 16777045 -> 14;
            case 16777215 -> 15;
            default -> -1;
        };
    }

    public static int rgbToClosestVanillaColor(int rgb) {
        int nearestColor = 0;
        double shortestDistance = 0x407B9AC46D6FF45EL; // sqrt(3*255*255)
        for (int color : VANILLA_COLORS) {
             double newDistance = colorDistance(color, rgb);
             if (newDistance < shortestDistance) {
                 nearestColor = color;
                 shortestDistance = newDistance;
             }
        }
        return nearestColor;
    }

    public static int rgbToClosestColorIndex(int rgb) {
        int colorIndex = rgbToColorIndex(rgb);
        return colorIndex < 0 ? rgbToColorIndex(rgbToClosestVanillaColor(rgb)) : colorIndex;
    }

    public static double colorDistance(int rgb1, int rgb2) {
        int dr = ((rgb1 >> 16) & 0xFF) - ((rgb2 >> 16) & 0xFF);
        int dg = ((rgb1 >> 8) & 0xFF) - ((rgb2 >> 8) & 0xFF);
        int db = (rgb1 & 0xFF) - (rgb2 & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    public static int hexCodeToRgb(String hexCode, boolean withHash) {
        if (withHash) {
            hexCode = hexCode.substring(1);
        }
        try {
            return Integer.parseInt(hexCode, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String rgbToHexCode(int rgb, boolean withHash) {
        return withHash ? String.format("#%06X", rgb) : String.format("%06X", rgb);
    }

    public static String rgbToNameOrHexCode(int rgb, boolean withHash) {
        String colorName = rgbToColorName(rgb);
        return Objects.requireNonNullElseGet(colorName, () -> rgbToHexCode(rgb, withHash));
    }

    public static int colorNameOrHexCodeToRgb(String colorName, boolean withHash) {
        int rgb = colorNameToRgb(colorName);
        return rgb < 0 ? hexCodeToRgb(colorName, withHash) : rgb;
    }

    public static int randomColor() {
        return ThreadLocalRandom.current().nextInt(0x1000000);
    }

    public static int getTintColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) {
            return 0;
        } else if (r == max) {
            return 0xFF0000 | (g * 255 / max) << 8 | (b * 255 / max);
        } else if (g == max) {
            return (r * 255 / max) << 16 | 0xFF00 | (b * 255 / max);
        } else {
            return (r * 255 / max) << 16 | (g * 255 / max) << 8 | 0xFF;
        }
    }

    public static int getShadeColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r < g) {
            if (b < r) {
                // b < r < g
                return getShadeColorChannelValue(b, r, g) << 16 | g << 8;
            } else if (b > g) {
                // r < g < b
                return getShadeColorChannelValue(r, g, b) << 8 | b;
            } else {
                // r < b < g
                // special case: two equal max
                return g << 8 | getShadeColorChannelValue(r, b, g);
            }
        } else {
            if (b < g) {
                // b < g < r
                return r << 16 | getShadeColorChannelValue(b, g, r) << 8;
            } else if (b > r) {
                // g < r < b
                // special case: two equal min
                return getShadeColorChannelValue(g, r, b) << 16 | b;
            } else {
                // g < b < r
                // special case: all three equal
                return r << 16 | getShadeColorChannelValue(g, b, r);
            }
        }
    }

    /**
     * helper function for {@link #getShadeColor(int)}
     * */
    private static int getShadeColorChannelValue(int minChannel, int medChannel, int maxChannel) {
        if (minChannel == maxChannel) {
            return 0;
        }
        return ((medChannel - minChannel) * maxChannel) / (maxChannel - minChannel);
    }

    public static int getPureHueFromRGB(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (r < g) {
            if (b < r) {
                // b < r < g
                return getPureHueChannelValue(b, r, g) << 16 | 0xFF00;
            } else if (b > g) {
                // r < g < b
                return getPureHueChannelValue(r, g, b) << 8 | 0xFF;
            } else {
                // r < b < g
                // special case: two equal max
                return 0xFF00 | getPureHueChannelValue(r, b, g);
            }
        } else {
            if (b < g) {
                // b < g < r
                return 0xFF0000 | getPureHueChannelValue(b, g, r) << 8;
            } else if (b > r) {
                // g < r < b
                // special case: two equal min
                return getPureHueChannelValue(g, r, b) << 16 | 0xFF;
            } else {
                // g < b < r
                // special case: all three equal
                return 0xFF0000 | getPureHueChannelValue(g, b, r);
            }
        }
    }

    /**
     * helper function for {@link #getPureHueFromRGB(int)}
     * */
    private static int getPureHueChannelValue(int minChannel, int medChannel, int maxChannel) {
        return (medChannel - minChannel) * 255 / (maxChannel - minChannel);
    }

    public static int[] RGBtoHSV(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // [h, s, v, hueColor]
        int[] hsvData = new int[4];

        // delta = max - min
        // ld = med - min
        // hueColor = ld * 255 / delta
        // v = max / 255 * 100
        // s = delta / max * 100
        // h = offset +/- (ld * 60 + (delta >> 1)) / delta
        if (r < g) {
            if (b < r) {
                // b < r < g
                int delta = g - b;
                int ld = r - b;
                int hd = delta >> 1;
                hsvData[0] = 120 - (ld * 60 + hd) / delta;
                hsvData[1] = (delta * 100 + (g >> 1)) / g;
                hsvData[2] = (g * 100 + 127) / 255;
                hsvData[3] = ((ld * 255 + hd) / delta) << 16 | 0xFF00FF00;
            } else if (b > g) {
                // r < g < b
                int delta = b - r;
                int ld = g - r;
                int hd = delta >> 1;
                hsvData[0] = 240 - (ld * 60 + hd) / delta;
                hsvData[1] = (delta * 100 + (b >> 1)) / b;
                hsvData[2] = (b * 100 + 127) / 255;
                hsvData[3] = ((ld * 255 + hd) / delta) << 8 | 0xFF0000FF;
            } else {
                // r < b < g
                int delta = g - r;
                int ld = b - r;
                int hd = delta >> 1;
                hsvData[0] = 120 + (ld * 60 + hd) / delta;
                hsvData[1] = (delta * 100 + (g >> 1)) / g;
                hsvData[2] = (g * 100 + 127) / 255;
                hsvData[3] = 0xFF00FF00 | ((ld * 255 + hd) / delta);
            }
        } else {
            if (b < g) {
                // b < g < r
                int delta = r - b;
                int ld = g - b;
                int hd = delta >> 1;
                hsvData[0] = (ld * 60 + hd) / delta;
                hsvData[1] = (delta * 100 + (r >> 1)) / r;
                hsvData[2] = (r * 100 + 127) / 255;
                hsvData[3] = 0xFFFF0000 | ((ld * 255 + hd) / delta) << 8;
            } else if (b > r) {
                // g < r < b
                int delta = b - g;
                int ld = r - g;
                int hd = delta >> 1;
                hsvData[0] = 240 + (ld * 60 + hd) / delta;
                hsvData[1] = (delta * 100 + (b >> 1)) / b;
                hsvData[2] = (b * 100 + 127) / 255;
                hsvData[3] = ((ld * 255 + hd) / delta) << 16 | 0xFF0000FF;
            } else {
                // g < b < r
                // special case: all three equal
                if (r == g) {
                    hsvData[2] = (r * 100 + 127) / 255;
                    hsvData[3] = 0xFFFF0000;
                    return hsvData;
                }
                int delta = r - g;
                int ld = b - g;
                int hd = delta >> 1;
                int h = 360 - (ld * 60 + hd) / delta;
                if (h == 360) h = 0;
                hsvData[0] = h;
                hsvData[1] = (delta * 100 + (r >> 1)) / r;
                hsvData[2] = (r * 100 + 127) / 255;
                hsvData[3] = 0xFFFF0000 | ((ld * 255 + hd) / delta);
            }
        }
        return hsvData;
    }


    public static int HSVtoRGB(int h, int s, int v) {
        if (v == 0) return 0xFF000000;
        int max = (v * 255) / 100;

        if (s == 0) {
            return 0xFF000000 | (max << 16) | (max << 8) | max;
        }

        int region = h / 60;
        int remainder = h % 60;
        int min = (max * (100 - s)) / 100;
        int falling = (max * (10000 - (s * remainder * 100) / 60)) / 10000;
        int rising = (max * (10000 - (s * (60 - remainder) * 100) / 60)) / 10000;

        int r, g, b;

        switch (region) {
            case 0  -> {r = max;     g = rising;  b = min;    } // Red -> Yellow
            case 1  -> {r = falling; g = max;     b = min;    } // Yellow -> Green
            case 2  -> {r = min;     g = max;     b = rising; } // Green -> Cyan
            case 3  -> {r = min;     g = falling; b = max;    } // Cyan -> Blue
            case 4  -> {r = rising;  g = min;     b = max;    } // Blue -> Magenta
            default -> {r = max;     g = min;     b = falling;} // Magenta -> Red
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int getPureHue(int hue) {
        int region = hue / 60;

        int remainder = hue % 60;
        int rising = (remainder * 255) / 60;
        int falling = 255 - rising;

        int r, g, b;

        switch (region) {
            case 0: r = 255;     g = rising;  b = 0;       break; // Red -> Yellow
            case 1: r = falling; g = 255;     b = 0;       break; // Yellow -> Green
            case 2: r = 0;       g = 255;     b = rising;  break; // Green -> Cyan
            case 3: r = 0;       g = falling; b = 255;     break; // Cyan -> Blue
            case 4: r = rising;  g = 0;       b = 255;     break; // Blue -> Magenta
            default:r = 255;     g = 0;       b = falling; break; // Magenta -> Red
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
