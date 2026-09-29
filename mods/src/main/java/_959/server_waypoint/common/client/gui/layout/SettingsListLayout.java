package _959.server_waypoint.common.client.gui.layout;

/**
 * Pure geometry for {@code SettingsListWidget}: columns, label width, entry offsets, reveal
 * scrolling and visibility. Values are pixels; offsets are relative to the top of the list content.
 */
public final class SettingsListLayout {
    public static final int SECTION_GAP = 6;
    public static final int LABEL_GAP = 8;
    public static final int SUFFIX_GAP = 3;
    public static final int ACTION_GAP = 4;
    public static final int MIN_LABEL_WIDTH = 40;
    public static final int ROW_VERTICAL_PADDING = 4;
    public static final int MIN_ROW_HEIGHT = 21;
    public static final int HEADER_BOTTOM_PADDING = 4;

    private SettingsListLayout() {
    }

    /** The unit column plus its gap, or zero when no row has a unit. */
    public static int suffixSlot(int suffixColumnWidth) {
        return suffixColumnWidth > 0 ? SUFFIX_GAP + suffixColumnWidth : 0;
    }

    /** The action column plus its gap, or zero when no row has an action. */
    public static int actionSlot(int actionColumnWidth) {
        return actionColumnWidth > 0 ? ACTION_GAP + actionColumnWidth : 0;
    }

    /** The width a row's label may wrap to, never less than {@link #MIN_LABEL_WIDTH}. */
    public static int labelWidth(int rowWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth) {
        int width = rowWidth - controlWidth - LABEL_GAP
                - suffixSlot(suffixColumnWidth) - actionSlot(actionColumnWidth);
        return Math.max(MIN_LABEL_WIDTH, width);
    }

    /** The width a row needs to show its label on one line. */
    public static int rowPreferredWidth(int labelWidth, int controlWidth, int suffixColumnWidth, int actionColumnWidth) {
        return labelWidth + LABEL_GAP + controlWidth + suffixSlot(suffixColumnWidth) + actionSlot(actionColumnWidth);
    }

    /** A row's tallest part plus padding above and below, and at least {@link #MIN_ROW_HEIGHT}. */
    public static int rowHeight(int tallestPartHeight) {
        return Math.max(MIN_ROW_HEIGHT, tallestPartHeight + ROW_VERTICAL_PADDING * 2);
    }

    /** A header's title plus the padding below it. */
    public static int headerHeight(int titleHeight) {
        return titleHeight + HEADER_BOTTOM_PADDING;
    }

    /**
     * The top of each entry, with {@link #SECTION_GAP} before every header except the first entry.
     * The returned array has one more element than {@code heights}; the last one is the content height.
     */
    public static int[] entryTops(int[] heights, boolean[] headers) {
        if (heights.length != headers.length) {
            throw new IllegalArgumentException("heights and headers have different lengths");
        }
        int[] tops = new int[heights.length + 1];
        int y = 0;
        for (int i = 0; i < heights.length; i++) {
            if (headers[i] && i > 0) {
                y += SECTION_GAP;
            }
            tops[i] = y;
            y += heights[i];
        }
        tops[heights.length] = y;
        return tops;
    }

    /** Whether the span from {@code top} to {@code bottom} lies entirely inside the viewport. */
    public static boolean fullyVisible(int top, int bottom, int viewportTop, int viewportBottom) {
        return top >= viewportTop && bottom <= viewportBottom;
    }

    /**
     * The scroll position that fully shows the focused row while moving as little as possible. When
     * they fit, it also shows everything down to {@code nextBottom} and then up to
     * {@code previousTop}. A row taller than the viewport shows its top. The result stays within the
     * content.
     */
    public static double revealScroll(double scroll, int viewportHeight, int contentHeight,
                                      int focusedTop, int focusedBottom, int previousTop, int nextBottom) {
        double maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (focusedBottom - focusedTop > viewportHeight) {
            return clamp(focusedTop, 0, maxScroll);
        }
        double lowest = focusedBottom - viewportHeight;
        double highest = focusedTop;
        int bottom = focusedBottom;
        if (nextBottom - focusedTop <= viewportHeight) {
            lowest = Math.max(lowest, nextBottom - viewportHeight);
            bottom = nextBottom;
        }
        if (bottom - previousTop <= viewportHeight) {
            highest = Math.min(highest, previousTop);
        }
        return clamp(clamp(scroll, lowest, highest), 0, maxScroll);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
