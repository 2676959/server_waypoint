package _959.server_waypoint.common.client.gui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the waypoint add and edit forms, worked out from measured sizes: the widths of the
 * columns, the gap between rows, and where the header, the rows and the footer go. It knows nothing
 * about widgets or fonts, so the screens measure their text, ask it where things go and place their
 * widgets from the answers.
 */
final class WaypointFormLayout {
    static final int SCREEN_MARGIN = 10;
    /** Between the header and the panel, and between the panel and the footer. */
    static final int SECTION_GAP = 6;
    static final int TITLE_SUBTITLE_GAP = 2;
    /** The panel's 1-pixel outline plus 7 pixels of padding, on each side. */
    static final int PANEL_INSET = 8;
    static final int MAX_CONTROL_WIDTH = 240;
    /** Between the widest label and the control column. */
    static final int LABEL_PADDING = 10;
    static final int MAX_ROW_GAP = 9;
    static final int MIN_ROW_GAP = 5;
    static final int DIVIDER_HEIGHT = 1;
    /** Between the fields of one row. */
    static final int FIELD_GAP = 10;
    /** From an inline label to its field. */
    static final int INLINE_GAP = 5;
    /** From an axis letter to its field. */
    static final int AXIS_GAP = 4;
    /** From the swatch to the hex field, and between the icon preview, dropdown and remove button. */
    static final int CONTROL_GAP = 4;
    static final int MIN_COORDINATE_WIDTH = 30;
    static final int MAX_COORDINATE_WIDTH = 48;
    /** The Initials and Yaw fields. */
    static final int SMALL_FIELD_WIDTH = 26;
    static final int TOGGLE_WIDTH = 48;
    static final int FOOTER_BUTTON_GAP = 6;
    /** Between the status message and the buttons beside it. */
    static final int STATUS_GAP = 8;
    /** Between the status message and the buttons below it. */
    static final int STATUS_LINE_GAP = 4;

    private WaypointFormLayout() {
    }

    /** The widths of the panel and its two columns. */
    record Columns(int panelWidth, int labelWidth, int controlWidth) {
        /** The width a label can take before it wraps onto another line. */
        int labelTextWidth() {
            return Math.max(1, this.labelWidth - LABEL_PADDING);
        }
    }

    /** A row or a divider of the panel. */
    record Item(int height, boolean isDivider) {
        static Item row(int height) {
            return new Item(height, false);
        }

        static Item divider() {
            return new Item(DIVIDER_HEIGHT, true);
        }
    }

    /** Where the parts go, with y positions on the screen. */
    record Arrangement(
            int rowGap,
            int groupTop,
            int groupHeight,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int labelX,
            int controlX,
            List<Integer> itemTops,
            boolean statusAbove,
            int statusY,
            int buttonsY,
            int buttonsHeight
    ) {
    }

    /**
     * The panel and column widths. The control column takes 240 pixels when the screen allows, then
     * shrinks, but never below {@code minimumControlWidth}; after that the label column narrows and
     * its labels wrap.
     */
    static Columns columns(int screenWidth, int widestLabelWidth, int minimumControlWidth) {
        int available = Math.max(0, screenWidth - 2 * SCREEN_MARGIN - 2 * PANEL_INSET);
        int label = widestLabelWidth + LABEL_PADDING;
        int control = MAX_CONTROL_WIDTH;
        if (label + control > available) {
            control = Math.min(available, Math.max(minimumControlWidth, available - label));
            label = Math.min(label, available - control);
        }
        return new Columns(label + control + 2 * PANEL_INSET, label, control);
    }

    /**
     * The control column's smallest width: the Position row with the smallest coordinate fields and
     * at least a field gap before Yaw, or the Color row, whichever needs more.
     */
    static int minimumControlWidth(int axisLetterWidth, int yawLabelWidth, int colorGroupWidth, int visibilityLabelWidth) {
        int position = 3 * (axisLetterWidth + AXIS_GAP + MIN_COORDINATE_WIDTH) + 3 * FIELD_GAP
                + yawLabelWidth + INLINE_GAP + SMALL_FIELD_WIDTH;
        int color = colorGroupWidth + FIELD_GAP + visibilityLabelWidth + INLINE_GAP + TOGGLE_WIDTH;
        return Math.max(position, color);
    }

    /**
     * The width of each coordinate field: an equal share of the Position row's free space, between
     * 30 and 48 pixels. The gap before Yaw takes what's left.
     */
    static int coordinateFieldWidth(int controlWidth, int axisLetterWidth, int yawLabelWidth) {
        int fixed = 3 * (axisLetterWidth + AXIS_GAP) + 3 * FIELD_GAP + yawLabelWidth + INLINE_GAP + SMALL_FIELD_WIDTH;
        return Math.max(MIN_COORDINATE_WIDTH, Math.min(MAX_COORDINATE_WIDTH, Math.floorDiv(controlWidth - fixed, 3)));
    }

    /** The width of a field that fills its row up to a field gap before a group of fixed width. */
    static int stretchedWidth(int controlWidth, int rightGroupWidth) {
        return Math.max(0, controlWidth - rightGroupWidth - FIELD_GAP);
    }

    /** The icon dropdown's width: the rest of its row after the preview and the remove button. */
    static int iconDropdownWidth(int controlWidth, int previewWidth, int removeButtonWidth) {
        return Math.max(0, controlWidth - previewWidth - removeButtonWidth - 2 * CONTROL_GAP);
    }

    /** The width of the footer's buttons together, given each button's outer width. */
    static int buttonsWidth(int... buttonWidths) {
        int width = 0;
        for (int buttonWidth : buttonWidths) {
            width += buttonWidth;
        }
        return width + FOOTER_BUTTON_GAP * Math.max(0, buttonWidths.length - 1);
    }

    /** The width the status message wraps to: beside the buttons, or a full line above them. */
    static int statusWidth(int panelWidth, int buttonsWidth) {
        return ClientConfigScreen.statusAboveButtons(panelWidth, buttonsWidth)
                ? panelWidth
                : panelWidth - buttonsWidth - STATUS_GAP;
    }

    /**
     * Places the header, panel and footer as one group centered on the screen. The gap between rows is
     * the largest from 9 down to 5 pixels at which the group fits inside the screen margins; if none
     * does, the group starts at the top margin.
     *
     * @param headerHeight the title, plus the subtitle and the gap under the title when there is one
     * @param items the panel's rows and dividers, top to bottom, with each row's height
     * @param buttonsWidth the footer's buttons together, see {@link #buttonsWidth}
     * @param buttonHeight the footer buttons' outer height
     * @param statusHeight the height of the status message wrapped to {@link #statusWidth}, 0 for none
     */
    static Arrangement arrange(
            int screenWidth,
            int screenHeight,
            Columns columns,
            int headerHeight,
            List<Item> items,
            int buttonsWidth,
            int buttonHeight,
            int statusHeight
    ) {
        boolean statusAbove = ClientConfigScreen.statusAboveButtons(columns.panelWidth(), buttonsWidth);
        int footerHeight = statusAbove && statusHeight > 0
                ? statusHeight + STATUS_LINE_GAP + buttonHeight
                : Math.max(buttonHeight, statusHeight);
        int fixedHeight = headerHeight + 2 * SECTION_GAP + 2 * PANEL_INSET + footerHeight;
        int rowGap = rowGap(items, screenHeight - 2 * SCREEN_MARGIN - fixedHeight);
        int panelHeight = 2 * PANEL_INSET + contentHeight(items, rowGap);
        int groupHeight = headerHeight + SECTION_GAP + panelHeight + SECTION_GAP + footerHeight;
        int groupTop = Math.max(SCREEN_MARGIN, (screenHeight - groupHeight) >> 1);
        int panelX = (screenWidth - columns.panelWidth()) >> 1;
        int panelY = groupTop + headerHeight + SECTION_GAP;

        List<Integer> itemTops = new ArrayList<>(items.size());
        int y = panelY + PANEL_INSET;
        for (int i = 0; i < items.size(); i++) {
            itemTops.add(y);
            y += items.get(i).height();
            if (i + 1 < items.size()) {
                y += gapBetween(items.get(i), items.get(i + 1), rowGap);
            }
        }

        int footerY = panelY + panelHeight + SECTION_GAP;
        int buttonsHeight = statusAbove ? buttonHeight : footerHeight;
        int statusY = statusAbove ? footerY : footerY + ((footerHeight - statusHeight) >> 1);
        return new Arrangement(
                rowGap,
                groupTop,
                groupHeight,
                panelX,
                panelY,
                columns.panelWidth(),
                panelHeight,
                panelX + PANEL_INSET,
                panelX + PANEL_INSET + columns.labelWidth(),
                List.copyOf(itemTops),
                statusAbove,
                statusY,
                footerY + footerHeight - buttonsHeight,
                buttonsHeight
        );
    }

    /** The largest row gap from 9 down to 5 at which the items fit {@code availableHeight}, else 5. */
    static int rowGap(List<Item> items, int availableHeight) {
        for (int gap = MAX_ROW_GAP; gap > MIN_ROW_GAP; gap--) {
            if (contentHeight(items, gap) <= availableHeight) {
                return gap;
            }
        }
        return MIN_ROW_GAP;
    }

    /** The items' heights and the gaps between them. */
    static int contentHeight(List<Item> items, int rowGap) {
        int height = 0;
        for (int i = 0; i < items.size(); i++) {
            height += items.get(i).height();
            if (i + 1 < items.size()) {
                height += gapBetween(items.get(i), items.get(i + 1), rowGap);
            }
        }
        return height;
    }

    /** Around a divider the gap is the row gap minus 2, above it and below it. */
    private static int gapBetween(Item above, Item below, int rowGap) {
        return above.isDivider() || below.isDivider() ? Math.max(0, rowGap - 2) : rowGap;
    }
}
