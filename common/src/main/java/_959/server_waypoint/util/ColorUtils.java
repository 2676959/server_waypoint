package _959.server_waypoint.util;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ColorUtils {
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
}
