package _959.server_waypoint.common.client.gui.screens;

import java.util.function.IntUnaryOperator;

/**
 * The geometry of the widget theme editor, worked out from measured sizes: the width of the group and
 * of its left column, the height of the panels and of the footer, and where the header line, the three
 * panels and the footer go. It knows nothing about widgets or fonts, so the screen measures its text,
 * asks it where things go and places its widgets from the answers.
 */
final class WidgetThemeEditorLayout {
    static final int SCREEN_MARGIN = 10;
    /** Between the header line and the panels, and between the panels and the footer. */
    static final int SECTION_GAP = 6;
    /** Between the key list, the key editor and the preview. */
    static final int PANEL_GAP = 2;
    /** Inside each panel, on every side. */
    static final int PANEL_PADDING = 6;
    /** The header line: the title at 1.2 times the font, and the dropdown, a line of text plus 2 pixels. */
    static final int HEADER_HEIGHT = 11;
    /** The least between the title and the dropdown. */
    static final int TITLE_GAP = 8;
    static final int MAX_GROUP_WIDTH = 420;
    /** The left column, with the key list and the key editor, is half the group but within these. */
    static final int MIN_LEFT_WIDTH = 152;
    static final int MAX_LEFT_WIDTH = 210;
    static final int MAX_PANELS_HEIGHT = 230;
    static final int EDITOR_HEIGHT = 53;
    static final int KEY_ROW_HEIGHT = 12;
    /** The key list keeps at least this many rows in view. */
    static final int MIN_KEY_ROWS = 4;
    /** The preview keeps at least its header and one row in view. */
    static final int MIN_PREVIEW_HEIGHT = 40;
    /** Between the status message and the buttons beside it. */
    static final int STATUS_GAP = 8;
    /** Between the status message and the buttons below it. */
    static final int STATUS_LINE_GAP = 4;
    /** From a key row's left edge to its key: a 2-pixel inset, the 8-pixel color chip and 4 pixels. */
    static final int KEY_X = 14;
    /** Between the widest key and a value. */
    static final int VALUE_GAP = 8;
    /** From a value to the right edge of its row. */
    static final int VALUE_INSET = 2;

    /** The least the panels take: the key editor and four key rows with the list's padding, 115 pixels. */
    private static final int MIN_PANELS_HEIGHT = Math.max(
            MIN_PREVIEW_HEIGHT,
            EDITOR_HEIGHT + PANEL_GAP + MIN_KEY_ROWS * KEY_ROW_HEIGHT + 2 * PANEL_PADDING
    );

    private WidgetThemeEditorLayout() {
    }

    /** A rectangle on the screen. */
    record Rect(int x, int y, int width, int height) {
        int right() {
            return this.x + this.width;
        }

        int bottom() {
            return this.y + this.height;
        }
    }

    /**
     * Where the parts go, with positions on the screen. The panels' rectangles are their visual
     * bounds, outline included. {@code buttons} is the row the footer's buttons are centered in: as
     * tall as the buttons, or as the footer when a status taller than them sits beside them.
     */
    record Arrangement(
            Rect group,
            Rect title,
            Rect dropdown,
            Rect keyList,
            Rect editor,
            Rect preview,
            Rect buttons,
            Rect status,
            boolean statusAbove
    ) {
    }

    /**
     * Places the header line, the three panels and the footer as one group centered on the screen. The
     * panels take the height left after the margins, the header line, the footer and the gaps, up to 230
     * pixels, but not less than the key list needs for four rows above the key editor; if the screen is
     * too short for that, the group starts at the top margin. The title takes the header line's width
     * that the dropdown and the gap leave, none when they leave nothing.
     *
     * @param dropdownWidth the theme dropdown's width
     * @param buttonsWidth the footer's buttons together, with the gaps between them
     * @param buttonsHeight the footer buttons' outer height
     * @param statusHeight the height of the status message wrapped to the width it is given, 0 for none
     */
    static Arrangement arrange(
            int screenWidth,
            int screenHeight,
            int dropdownWidth,
            int buttonsWidth,
            int buttonsHeight,
            IntUnaryOperator statusHeight
    ) {
        int groupWidth = Math.max(0, Math.min(MAX_GROUP_WIDTH, screenWidth - 2 * SCREEN_MARGIN));
        boolean statusAbove = ClientConfigScreen.statusAboveButtons(groupWidth, buttonsWidth);
        int statusWidth = statusAbove ? groupWidth : groupWidth - buttonsWidth - STATUS_GAP;
        int wrappedStatusHeight = statusHeight.applyAsInt(statusWidth);
        int footerHeight = statusAbove && wrappedStatusHeight > 0
                ? wrappedStatusHeight + STATUS_LINE_GAP + buttonsHeight
                : Math.max(buttonsHeight, wrappedStatusHeight);
        int availableHeight = screenHeight - 2 * SCREEN_MARGIN - HEADER_HEIGHT - 2 * SECTION_GAP - footerHeight;
        int panelsHeight = Math.max(MIN_PANELS_HEIGHT, Math.min(MAX_PANELS_HEIGHT, availableHeight));
        int groupHeight = HEADER_HEIGHT + SECTION_GAP + panelsHeight + SECTION_GAP + footerHeight;
        int groupX = (screenWidth - groupWidth) >> 1;
        int groupY = Math.max(SCREEN_MARGIN, (screenHeight - groupHeight) >> 1);
        Rect group = new Rect(groupX, groupY, groupWidth, groupHeight);

        int titleWidth = Math.max(0, groupWidth - dropdownWidth - TITLE_GAP);
        Rect title = new Rect(groupX, groupY, titleWidth, HEADER_HEIGHT);
        Rect dropdown = new Rect(group.right() - dropdownWidth, groupY, dropdownWidth, HEADER_HEIGHT);

        int panelsY = groupY + HEADER_HEIGHT + SECTION_GAP;
        int leftWidth = leftColumnWidth(groupWidth);
        Rect keyList = new Rect(groupX, panelsY, leftWidth, panelsHeight - PANEL_GAP - EDITOR_HEIGHT);
        Rect editor = new Rect(groupX, keyList.bottom() + PANEL_GAP, leftWidth, EDITOR_HEIGHT);
        Rect preview = new Rect(
                groupX + leftWidth + PANEL_GAP,
                panelsY,
                groupWidth - leftWidth - PANEL_GAP,
                panelsHeight
        );

        int footerY = panelsY + panelsHeight + SECTION_GAP;
        int buttonsRowHeight = statusAbove ? buttonsHeight : footerHeight;
        int buttonsRowY = footerY + footerHeight - buttonsRowHeight;
        Rect buttons = new Rect(group.right() - buttonsWidth, buttonsRowY, buttonsWidth, buttonsRowHeight);
        int statusY = statusAbove ? footerY : footerY + ((footerHeight - wrappedStatusHeight) >> 1);
        Rect status = new Rect(groupX, statusY, statusWidth, wrappedStatusHeight);
        return new Arrangement(group, title, dropdown, keyList, editor, preview, buttons, status, statusAbove);
    }

    /** The width of the left column, which holds the key list and the key editor: half the group, 152 to 210. */
    static int leftColumnWidth(int groupWidth) {
        return Math.max(MIN_LEFT_WIDTH, Math.min(MAX_LEFT_WIDTH, Math.round(groupWidth * 0.5F)));
    }

    /**
     * Whether the key rows show their values: only when a row is wide enough for the widest key, a value
     * and the gaps and insets around them. Narrower rows show the keys alone.
     */
    static boolean showsValues(int rowWidth, int widestKeyWidth, int valueWidth) {
        return KEY_X + widestKeyWidth + VALUE_GAP + valueWidth + VALUE_INSET <= rowWidth;
    }
}
