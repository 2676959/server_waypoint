package _959.server_waypoint.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

public class ColorUtils {
    private static final int OKLCH_HUE_SORT_REGIONS = 24;
    private static final double OKLCH_GREYSCALE_TAIL_MAX_CHROMA = 0.005D;
    private static final double OKLCH_IN_REGION_MAX_GREY_CHROMA = 0.080D;
    private static final double OKLCH_IN_REGION_MIN_BRIGHT_LIGHTNESS = 0.86D;
    private static final double OKLCH_IN_REGION_MAX_BRIGHT_CHROMA = 0.160D;
    private static final double OKLCH_TWO_OPT_EPSILON = 0.0000000001D;
    private static final int OKLCH_TWO_OPT_MAX_PASSES = 8;
    private static final double OKLCH_GRADIENT_BACKWARD_LIGHTNESS_PENALTY = 16.0D;
    private static final int OKLCH_GRADIENT_SMOOTHING_LOOKAHEAD = 8;
    private static final int OKLCH_GRADIENT_TWO_OPT_MAX_PATH_SIZE = 160;
    private static final double OKLCH_GRADIENT_BRIGHT_MIN_LIGHTNESS = 0.70D;
    private static final double OKLCH_GRADIENT_DARK_MAX_LIGHTNESS = 0.50D;

    private enum OklchGradientPhase {
        PALE,
        BRIGHT,
        VIVID,
        DARK
    }

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
            hexCode = hexCode.length() > 6 ? hexCode.substring(0, 6) : hexCode;
            return Integer.parseInt(hexCode, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Hex code format: RRGGBB no alpha channel
     * */
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

    /**
     * Color format: RGB no alpha channel
     * */
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
            case 0,6-> {r = max;     g = rising;  b = min;    } // Red -> Yellow
            case 1  -> {r = falling; g = max;     b = min;    } // Yellow -> Green
            case 2  -> {r = min;     g = max;     b = rising; } // Green -> Cyan
            case 3  -> {r = min;     g = falling; b = max;    } // Cyan -> Blue
            case 4  -> {r = rising;  g = min;     b = max;    } // Blue -> Magenta
            default -> {r = max;     g = min;     b = falling;} // Magenta -> Red
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static <T> void sortWaypointColors(
            List<T> values,
            ToIntFunction<T> rgbExtractor,
            @Nullable Comparator<T> tieBreaker
    ) {
        List<List<OklchColorSortEntry<T>>> regionBuckets = new ArrayList<>(OKLCH_HUE_SORT_REGIONS);
        for (int i = 0; i < OKLCH_HUE_SORT_REGIONS; i++) {
            regionBuckets.add(new ArrayList<>());
        }
        List<OklchColorSortEntry<T>> greyTail = new ArrayList<>();

        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            int rgb = rgbExtractor.applyAsInt(value) & 0xFFFFFF;
            OklchColor color = rgbToOklch(rgb);
            OklchColorSortEntry<T> entry = new OklchColorSortEntry<>(value, rgb, color, i);
            if (isOklchGreyscaleTailColor(color)) {
                greyTail.add(entry);
            } else {
                regionBuckets.get(oklchHueRegion(color)).add(entry);
            }
        }

        List<T> sortedValues = new ArrayList<>(values.size());
        for (List<OklchColorSortEntry<T>> region : regionBuckets) {
            appendSmoothedOklchRegion(sortedValues, region, tieBreaker);
        }

        greyTail.sort((entry1, entry2) -> compareOklchGreyTailEntries(entry1, entry2, tieBreaker));
        for (OklchColorSortEntry<T> entry : greyTail) {
            sortedValues.add(entry.value());
        }

        values.clear();
        values.addAll(sortedValues);
    }

    public static long oklchColorSortKey(int rgb) {
        OklchColor color = rgbToOklch(rgb & 0xFFFFFF);
        if (isOklchGreyscaleTailColor(color)) {
            return packOklchColorSortKey(
                    OKLCH_HUE_SORT_REGIONS,
                    0,
                    quantizeOklchComponent(color.lightness(), 1.0D),
                    quantizeOklchComponent(color.chroma(), 0.5D),
                    quantizeOklchComponent(color.hue(), 360.0D),
                    rgb
            );
        }

        OklchGradientPhase phase = oklchGradientPhase(color);
        double primary = phase == OklchGradientPhase.PALE ? color.lightness() : 1.0D - color.lightness();
        double secondary = switch (phase) {
            case PALE, BRIGHT -> color.chroma();
            case VIVID, DARK -> 0.5D - color.chroma();
        };
        return packOklchColorSortKey(
                oklchHueRegion(color),
                phase.ordinal(),
                quantizeOklchComponent(primary, 1.0D),
                quantizeOklchComponent(secondary, 0.5D),
                quantizeOklchComponent(color.hue(), 360.0D),
                rgb
        );
    }

    private static <T> void appendSmoothedOklchRegion(
            List<T> sortedValues,
            List<OklchColorSortEntry<T>> region,
            @Nullable Comparator<T> tieBreaker
    ) {
        List<List<OklchColorSortEntry<T>>> phaseBuckets = new ArrayList<>(OklchGradientPhase.values().length);
        for (int i = 0; i < OklchGradientPhase.values().length; i++) {
            phaseBuckets.add(new ArrayList<>());
        }

        for (OklchColorSortEntry<T> entry : region) {
            phaseBuckets.get(oklchGradientPhase(entry.color()).ordinal()).add(entry);
        }

        for (OklchGradientPhase phase : OklchGradientPhase.values()) {
            List<OklchColorSortEntry<T>> phaseEntries = new ArrayList<>(phaseBuckets.get(phase.ordinal()));
            phaseEntries.sort((entry1, entry2) -> compareOklchPhaseEntries(entry1, entry2, phase, tieBreaker));
            improveOklchPhasePathTwoOpt(phaseEntries, phase);
            for (OklchColorSortEntry<T> entry : phaseEntries) {
                sortedValues.add(entry.value());
            }
        }
    }

    private static void improveOklchPhasePathTwoOpt(
            List<? extends OklchColorSortEntry<?>> path,
            OklchGradientPhase phase
    ) {
        if (path.size() < 4 || path.size() > OKLCH_GRADIENT_TWO_OPT_MAX_PATH_SIZE) {
            return;
        }

        for (int pass = 0; pass < OKLCH_TWO_OPT_MAX_PASSES; pass++) {
            boolean improved = false;
            for (int i = 1; i < path.size() - 2; i++) {
                int maxEnd = Math.min(path.size() - 1, i + OKLCH_GRADIENT_SMOOTHING_LOOKAHEAD);
                for (int k = i + 1; k < maxEnd; k++) {
                    double oldCost = oklchPhaseTwoOptCost(path, phase, i, k, false);
                    double newCost = oklchPhaseTwoOptCost(path, phase, i, k, true);
                    if (newCost + OKLCH_TWO_OPT_EPSILON < oldCost) {
                        reverseOklchPath(path, i, k);
                        improved = true;
                    }
                }
            }
            if (!improved) {
                return;
            }
        }
    }

    private static double oklchPhaseTwoOptCost(
            List<? extends OklchColorSortEntry<?>> path,
            OklchGradientPhase phase,
            int start,
            int end,
            boolean reversed
    ) {
        double cost = 0.0D;
        if (reversed) {
            cost += oklchPhasePathConnectionCost(path, start - 1, end, phase);
            for (int i = end; i > start; i--) {
                cost += oklchPhasePathConnectionCost(path, i, i - 1, phase);
            }
            cost += oklchPhasePathConnectionCost(path, start, end + 1, phase);
        } else {
            cost += oklchPhasePathConnectionCost(path, start - 1, start, phase);
            for (int i = start; i < end; i++) {
                cost += oklchPhasePathConnectionCost(path, i, i + 1, phase);
            }
            cost += oklchPhasePathConnectionCost(path, end, end + 1, phase);
        }
        return cost;
    }

    private static double oklchPhasePathConnectionCost(
            List<? extends OklchColorSortEntry<?>> path,
            int index1,
            int index2,
            OklchGradientPhase phase
    ) {
        if (index1 < 0 || index2 >= path.size()) {
            return 0.0D;
        }
        return oklchPhaseStepCost(path.get(index1), path.get(index2), phase);
    }

    private static double oklchPhaseStepCost(
            OklchColorSortEntry<?> entry1,
            OklchColorSortEntry<?> entry2,
            OklchGradientPhase phase
    ) {
        double distance = cartesianOklchDistanceSquared(entry1.color(), entry2.color());
        double lightnessDelta = entry2.color().lightness() - entry1.color().lightness();
        double backwardLightnessDistance = switch (phase) {
            case PALE -> Math.max(0.0D, -lightnessDelta);
            case BRIGHT, VIVID, DARK -> Math.max(0.0D, lightnessDelta);
        };
        return distance + backwardLightnessDistance * backwardLightnessDistance * OKLCH_GRADIENT_BACKWARD_LIGHTNESS_PENALTY;
    }

    private static <T> int compareOklchPhaseEntries(
            OklchColorSortEntry<T> entry1,
            OklchColorSortEntry<T> entry2,
            OklchGradientPhase phase,
            @Nullable Comparator<T> tieBreaker
    ) {
        int lightnessCompare = switch (phase) {
            case PALE -> Double.compare(entry1.color().lightness(), entry2.color().lightness());
            case BRIGHT, VIVID, DARK -> Double.compare(entry2.color().lightness(), entry1.color().lightness());
        };
        if (lightnessCompare != 0) {
            return lightnessCompare;
        }

        int chromaCompare = switch (phase) {
            case PALE, BRIGHT -> Double.compare(entry1.color().chroma(), entry2.color().chroma());
            case VIVID, DARK -> Double.compare(entry2.color().chroma(), entry1.color().chroma());
        };
        if (chromaCompare != 0) {
            return chromaCompare;
        }
        return compareOklchTies(entry1, entry2, tieBreaker);
    }

    private static <T> int compareOklchGreyTailEntries(
            OklchColorSortEntry<T> entry1,
            OklchColorSortEntry<T> entry2,
            @Nullable Comparator<T> tieBreaker
    ) {
        int lightnessCompare = Double.compare(entry1.color().lightness(), entry2.color().lightness());
        if (lightnessCompare != 0) {
            return lightnessCompare;
        }
        return compareOklchTies(entry1, entry2, tieBreaker);
    }

    private static <T> int compareOklchTies(
            OklchColorSortEntry<T> entry1,
            OklchColorSortEntry<T> entry2,
            @Nullable Comparator<T> tieBreaker
    ) {
        int hueCompare = Double.compare(entry1.color().hue(), entry2.color().hue());
        if (hueCompare != 0) {
            return hueCompare;
        }
        int rgbCompare = Integer.compare(entry1.rgb(), entry2.rgb());
        if (rgbCompare != 0) {
            return rgbCompare;
        }
        if (tieBreaker != null) {
            int tieBreakerCompare = tieBreaker.compare(entry1.value(), entry2.value());
            if (tieBreakerCompare != 0) {
                return tieBreakerCompare;
            }
        }
        return Integer.compare(entry1.originalIndex(), entry2.originalIndex());
    }

    private static OklchGradientPhase oklchGradientPhase(OklchColor color) {
        if (color.lightness() <= OKLCH_GRADIENT_DARK_MAX_LIGHTNESS) {
            return OklchGradientPhase.DARK;
        }
        if (color.chroma() <= OKLCH_IN_REGION_MAX_GREY_CHROMA
                || (color.lightness() >= OKLCH_IN_REGION_MIN_BRIGHT_LIGHTNESS
                && color.chroma() <= OKLCH_IN_REGION_MAX_BRIGHT_CHROMA)) {
            return OklchGradientPhase.PALE;
        }
        if (color.lightness() >= OKLCH_GRADIENT_BRIGHT_MIN_LIGHTNESS) {
            return OklchGradientPhase.BRIGHT;
        }
        return OklchGradientPhase.VIVID;
    }

    private static boolean isOklchGreyscaleTailColor(OklchColor color) {
        return color.chroma() <= OKLCH_GREYSCALE_TAIL_MAX_CHROMA;
    }

    private static int oklchHueRegion(OklchColor color) {
        return Math.min((int) (color.hue() / (360.0D / OKLCH_HUE_SORT_REGIONS)), OKLCH_HUE_SORT_REGIONS - 1);
    }

    private static OklchColor rgbToOklch(int rgb) {
        double r = linearizeSrgb((rgb >> 16) & 0xFF);
        double g = linearizeSrgb((rgb >> 8) & 0xFF);
        double b = linearizeSrgb(rgb & 0xFF);

        double l = Math.cbrt(0.4122214708D * r + 0.5363325363D * g + 0.0514459929D * b);
        double m = Math.cbrt(0.2119034982D * r + 0.6806995451D * g + 0.1073969566D * b);
        double s = Math.cbrt(0.0883024619D * r + 0.2817188376D * g + 0.6299787005D * b);

        double lightness = 0.2104542553D * l + 0.7936177850D * m - 0.0040720468D * s;
        double a = 1.9779984951D * l - 2.4285922050D * m + 0.4505937099D * s;
        double bOpponent = 0.0259040371D * l + 0.7827717662D * m - 0.8086757660D * s;
        double hue = Math.toDegrees(Math.atan2(bOpponent, a));
        if (hue < 0.0D) {
            hue += 360.0D;
        }
        return new OklchColor(lightness, Math.hypot(a, bOpponent), hue);
    }

    private static double linearizeSrgb(int value) {
        double channel = value / 255.0D;
        if (channel <= 0.04045D) {
            return channel / 12.92D;
        }
        return Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }

    private static double cartesianOklchDistanceSquared(OklchColor color1, OklchColor color2) {
        double lightnessDistance = color1.lightness() - color2.lightness();
        double hue1 = Math.toRadians(color1.hue());
        double hue2 = Math.toRadians(color2.hue());
        double aDistance = color1.chroma() * Math.cos(hue1) - color2.chroma() * Math.cos(hue2);
        double bDistance = color1.chroma() * Math.sin(hue1) - color2.chroma() * Math.sin(hue2);
        return lightnessDistance * lightnessDistance + aDistance * aDistance + bDistance * bDistance;
    }

    private static void reverseOklchPath(List<? extends OklchColorSortEntry<?>> path, int start, int end) {
        while (start < end) {
            swapOklchPathEntries(path, start, end);
            start++;
            end--;
        }
    }

    private static <T extends OklchColorSortEntry<?>> void swapOklchPathEntries(List<T> path, int index1, int index2) {
        T entry = path.get(index1);
        path.set(index1, path.get(index2));
        path.set(index2, entry);
    }

    private static long packOklchColorSortKey(
            int region,
            int phase,
            int primary,
            int secondary,
            int hue,
            int rgb
    ) {
        return ((long) region & 0x1FL) << 58
                | ((long) phase & 0x3L) << 56
                | ((long) primary & 0xFFFFL) << 40
                | ((long) secondary & 0xFFFFL) << 24
                | ((long) hue & 0xFFFFL) << 8
                | (rgb & 0xFFL);
    }

    private static int quantizeOklchComponent(double value, double maxValue) {
        double clampedValue = Math.max(0.0D, Math.min(maxValue, value));
        return (int) Math.round(clampedValue * 65535.0D / maxValue);
    }

    private record OklchColor(double lightness, double chroma, double hue) {
    }

    private record OklchColorSortEntry<T>(T value, int rgb, OklchColor color, int originalIndex) {
    }

    public static int getPureHue(int hue) {
        int region = hue / 60;

        int remainder = hue % 60;
        int rising = (remainder * 255) / 60;
        int falling = 255 - rising;

        int r, g, b;

        switch (region) {
            case 0,6 -> {r = 255;     g = rising;  b = 0;      }
            case 1   -> {r = falling; g = 255;     b = 0;      }
            case 2   -> {r = 0;       g = 255;     b = rising; }
            case 3   -> {r = 0;       g = falling; b = 255;    }
            case 4   -> {r = rising;  g = 0;       b = 255;    }
            default  -> {r = 255;     g = 0;       b = falling;}
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * get black or white text color based on background color
     * */
    public static int getSafeTextColor(int rgb) {
        // Extract RGB components using bitwise shifting
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double bgLuminance = calculateRelativeLuminance(r >> 1, g >> 1, b >> 1);

        // WCAG contrast formula ratios
        double contrastWhite = 1.05 / (bgLuminance + 0.05);
        double contrastBlack = (bgLuminance + 0.05) / 0.05;

        return (contrastWhite > contrastBlack) ? 0xFFFFFFFF : 0xFF000000;
    }

    private static double calculateRelativeLuminance(int r, int g, int b) {
        // Normalize 0-255 integer to 0.0-1.0 double
        double red = linearize(r / 255.0);
        double green = linearize(g / 255.0);
        double blue = linearize(b / 255.0);

        // Standard coefficients for human color perception
        return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue);
    }

    private static double linearize(double c) {
        // sRGB gamma correction
        if (c <= 0.03928) {
            return c / 12.92;
        } else {
            return Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }
}
