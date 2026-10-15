package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Arrangement;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Columns;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The widths are those of English at the default GUI scale: the widest label is 55 pixels, an axis
 * letter 6, "Yaw" 18, "Visibility" 53, and the Color row's swatch and hex field take 60 together.
 */
class WaypointFormLayoutTest {
    private static final int WIDEST_LABEL = 55;
    private static final int AXIS = 6;
    private static final int YAW_LABEL = 18;
    private static final int COLOR_GROUP = 60;
    private static final int VISIBILITY_LABEL = 53;
    private static final int MINIMUM_CONTROL = WaypointFormLayout.minimumControlWidth(AXIS, YAW_LABEL, COLOR_GROUP, VISIBILITY_LABEL);
    private static final int BUTTON_HEIGHT = 13;
    private static final int TITLE_HEIGHT = 11;
    private static final int TITLE_AND_SUBTITLE_HEIGHT = 22;
    // Add: Dimension, List, a divider, Name, Icon, Color, Position, a divider, Keywords, Description.
    private static final List<Item> ADD_ITEMS = List.of(
            Item.row(11), Item.row(11), Item.divider(), Item.row(11), Item.row(13),
            Item.row(11), Item.row(11), Item.divider(), Item.row(11), Item.row(11));
    // Edit: Name, Display name, Icon, Color, Position, a divider, Keywords, Description.
    private static final List<Item> EDIT_ITEMS = List.of(
            Item.row(11), Item.row(11), Item.row(13), Item.row(11), Item.row(11),
            Item.divider(), Item.row(11), Item.row(11));

    @Test
    void theLabelColumnIsTheWidestLabelPlusTenPixels() {
        Columns columns = WaypointFormLayout.columns(480, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(65, columns.labelWidth());
        assertEquals(55, columns.labelTextWidth());
    }

    @Test
    void theControlColumnTakes240PixelsWhenTheScreenAllows() {
        Columns columns = WaypointFormLayout.columns(480, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(240, columns.controlWidth());
        // The label column, the control column and 16 pixels of outline and padding.
        assertEquals(65 + 240 + 16, columns.panelWidth());
    }

    @Test
    void theControlColumnShrinksToFitA320PixelScreen() {
        Columns columns = WaypointFormLayout.columns(320, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(300, columns.panelWidth());
        assertEquals(65, columns.labelWidth());
        assertEquals(219, columns.controlWidth());
    }

    @Test
    void theLabelColumnNarrowsWhenThePositionRowNeedsTheRoom() {
        Columns columns = WaypointFormLayout.columns(320, 120, MINIMUM_CONTROL);

        assertEquals(MINIMUM_CONTROL, columns.controlWidth());
        assertEquals(300, columns.panelWidth());
        assertEquals(284 - MINIMUM_CONTROL, columns.labelWidth());
        // The widest label is 120 pixels, so it wraps inside the narrower column.
        assertTrue(columns.labelTextWidth() < 120);
    }

    @Test
    void thePanelNeverExceedsTheScreenMinusItsMargins() {
        for (int width = 320; width <= 640; width += 10) {
            for (int label : new int[] {40, 55, 90, 140}) {
                Columns columns = WaypointFormLayout.columns(width, label, MINIMUM_CONTROL);
                assertTrue(columns.panelWidth() <= width - 20, width + "/" + label);
                assertTrue(columns.controlWidth() >= MINIMUM_CONTROL, width + "/" + label);
            }
        }
    }

    @Test
    void theMinimumControlWidthIsThePositionRowWithThirtyPixelFields() {
        // Three letters, gaps and 30-pixel fields, three field gaps, then "Yaw", 5 pixels and its 26-pixel field.
        assertEquals(3 * (6 + 4 + 30) + 3 * 10 + 18 + 5 + 26, MINIMUM_CONTROL);
        assertEquals(199, MINIMUM_CONTROL);
    }

    @Test
    void aLongVisibilityLabelMakesTheColorRowTheMinimum() {
        int minimum = WaypointFormLayout.minimumControlWidth(AXIS, YAW_LABEL, COLOR_GROUP, 90);

        assertEquals(COLOR_GROUP + 10 + 90 + 5 + 48, minimum);
        assertTrue(minimum > MINIMUM_CONTROL);
    }

    @Test
    void theCoordinateFieldsShareTheRoomBetween30And48Pixels() {
        assertEquals(43, WaypointFormLayout.coordinateFieldWidth(240, AXIS, YAW_LABEL));
        assertEquals(36, WaypointFormLayout.coordinateFieldWidth(219, AXIS, YAW_LABEL));
        assertEquals(30, WaypointFormLayout.coordinateFieldWidth(MINIMUM_CONTROL, AXIS, YAW_LABEL));
        assertEquals(48, WaypointFormLayout.coordinateFieldWidth(400, AXIS, YAW_LABEL));
        assertEquals(30, WaypointFormLayout.coordinateFieldWidth(150, AXIS, YAW_LABEL));
    }

    @Test
    void theGapBeforeYawIsAtLeastTenPixelsAtEveryControlWidth() {
        for (int control = MINIMUM_CONTROL; control <= 400; control++) {
            int field = WaypointFormLayout.coordinateFieldWidth(control, AXIS, YAW_LABEL);
            int rowWithoutTheGap = 3 * (AXIS + 4 + field) + 2 * 10 + YAW_LABEL + 5 + 26;
            assertTrue(control - rowWithoutTheGap >= 10, "control " + control);
        }
    }

    @Test
    void aStretchedFieldStopsTenPixelsBeforeTheGroupAtTheRight() {
        // Name: the "Initials" label (41 pixels), 5 pixels and a 26-pixel field.
        assertEquals(240 - (41 + 5 + 26) - 10, WaypointFormLayout.stretchedWidth(240, 41 + 5 + 26));
        assertEquals(0, WaypointFormLayout.stretchedWidth(50, 100));
    }

    @Test
    void theIconDropdownFillsTheRowBetweenThePreviewAndTheRemoveButton() {
        assertEquals(240 - 11 - 13 - 8, WaypointFormLayout.iconDropdownWidth(240, 11, 13));
    }

    @Test
    void theButtonsAreSixPixelsApart() {
        assertEquals(0, WaypointFormLayout.buttonsWidth());
        assertEquals(52, WaypointFormLayout.buttonsWidth(52));
        assertEquals(52 + 6 + 52 + 6 + 60, WaypointFormLayout.buttonsWidth(52, 52, 60));
    }

    @Test
    void theRowGapIs9ForAddAndEditAtBothSizes() {
        assertEquals(9, addAt(480, 270, 9).rowGap());
        assertEquals(9, addAt(320, 240, 9).rowGap());
        assertEquals(9, editAt(480, 270, 9).rowGap());
        assertEquals(9, editAt(320, 240, 9).rowGap());
    }

    @Test
    void theGroupAt320By240IsAsTallAsTheDesignSays() {
        Arrangement add = addAt(320, 240, 9);
        // Header 11 + gap 6 + panel 181 + gap 6 + footer 13.
        assertEquals(181, add.panelHeight());
        assertEquals(11 + 6 + 181 + 6 + 13, add.groupHeight());
        Arrangement edit = editAt(320, 240, 9);
        assertEquals(155, edit.panelHeight());
        assertEquals(22 + 6 + 155 + 6 + 13, edit.groupHeight());
    }

    @Test
    void theGroupIsCenteredOnTheScreen() {
        Arrangement add = addAt(320, 240, 9);

        assertEquals((240 - 217) >> 1, add.groupTop());
        assertEquals(add.groupTop() + 11 + 6, add.panelY());
        assertEquals(10, add.panelX());
        assertEquals(add.panelX() + 8, add.labelX());
        assertEquals(add.labelX() + 65, add.controlX());
        assertEquals((480 - 321) >> 1, addAt(480, 270, 9).panelX());
    }

    @Test
    void rowsAreSpacedByTheGapAndDividersByTheGapMinusTwoOnEachSide() {
        Arrangement add = addAt(320, 240, 9);
        List<Integer> tops = add.itemTops();

        int first = add.panelY() + 8;
        assertEquals(first, tops.get(0));
        assertEquals(first + 11 + 9, tops.get(1));
        // List (11), 7 pixels, the divider (1), 7 pixels, then Name.
        assertEquals(tops.get(1) + 11 + 7, tops.get(2));
        assertEquals(tops.get(2) + 1 + 7, tops.get(3));
        assertEquals(tops.get(3) + 11 + 9, tops.get(4));
        // The last row ends 8 pixels above the panel's bottom edge.
        assertEquals(add.panelY() + add.panelHeight() - 8, tops.get(9) + 11);
    }

    @Test
    void aWrappedStatusLowersTheGap() {
        // Two lines beside the buttons leave Add at 320x240 one pixel too tall at a gap of 9.
        assertEquals(8, addAt(320, 240, 18).rowGap());
        assertEquals(7, addAt(320, 240, 27).rowGap());
        assertEquals(9, editAt(320, 240, 18).rowGap());
    }

    @Test
    void theGapNeverDropsBelowFive() {
        Arrangement add = addAt(320, 100, 27);

        assertEquals(5, add.rowGap());
    }

    @Test
    void aGroupThatCannotFitStartsAtTheTopMargin() {
        Arrangement add = addAt(320, 150, 9);

        assertEquals(5, add.rowGap());
        assertEquals(10, add.groupTop());
    }

    @Test
    void theFooterIsAsTallAsTheStatusWhenItIsTallerThanTheButtons() {
        Arrangement add = addAt(320, 240, 27);

        assertFalse(add.statusAbove());
        // The status is centered on the footer, which is 27 pixels tall; the buttons fill it.
        int footerY = add.panelY() + add.panelHeight() + 6;
        assertEquals(footerY, add.statusY());
        assertEquals(footerY, add.buttonsY());
        assertEquals(27, add.buttonsHeight());
    }

    @Test
    void theStatusIsCenteredBesideShorterButtons() {
        Arrangement add = addAt(320, 240, 9);

        int footerY = add.panelY() + add.panelHeight() + 6;
        assertEquals(footerY + ((13 - 9) >> 1), add.statusY());
        assertEquals(footerY, add.buttonsY());
        assertEquals(13, add.buttonsHeight());
    }

    @Test
    void theStatusTakesItsOwnLineAboveButtonsThatLeaveItTooLittleRoom() {
        Columns columns = WaypointFormLayout.columns(320, WIDEST_LABEL, MINIMUM_CONTROL);
        // 300 - 270 - 8 leaves 22 pixels beside them, less than the 100 the status needs.
        int buttons = 270;
        assertEquals(300, WaypointFormLayout.statusWidth(columns.panelWidth(), buttons));
        Arrangement arrangement = WaypointFormLayout.arrange(320, 240, columns, TITLE_HEIGHT, ADD_ITEMS, buttons, BUTTON_HEIGHT, 18);

        assertTrue(arrangement.statusAbove());
        int footerHeight = 18 + 4 + 13;
        int footerY = arrangement.panelY() + arrangement.panelHeight() + 6;
        assertEquals(footerY, arrangement.statusY());
        assertEquals(footerY + footerHeight - 13, arrangement.buttonsY());
        assertEquals(13, arrangement.buttonsHeight());
        assertEquals(TITLE_HEIGHT + 6 + arrangement.panelHeight() + 6 + footerHeight, arrangement.groupHeight());
    }

    @Test
    void theStatusWrapsToTheRoomBesideTheButtonsWhenThereIsEnough() {
        assertEquals(300 - 165 - 8, WaypointFormLayout.statusWidth(300, 165));
        assertEquals(300, WaypointFormLayout.statusWidth(300, 270));
    }

    @Test
    void anEmptyStatusAddsNothingToTheFooter() {
        Arrangement withoutStatus = addAt(320, 240, 0);

        assertEquals(217, withoutStatus.groupHeight());
        assertEquals(13, withoutStatus.buttonsHeight());
    }

    private static Arrangement addAt(int width, int height, int statusHeight) {
        Columns columns = WaypointFormLayout.columns(width, WIDEST_LABEL, MINIMUM_CONTROL);
        return WaypointFormLayout.arrange(width, height, columns, TITLE_HEIGHT, ADD_ITEMS,
                WaypointFormLayout.buttonsWidth(52, 52), BUTTON_HEIGHT, statusHeight);
    }

    private static Arrangement editAt(int width, int height, int statusHeight) {
        Columns columns = WaypointFormLayout.columns(width, WIDEST_LABEL, MINIMUM_CONTROL);
        return WaypointFormLayout.arrange(width, height, columns, TITLE_AND_SUBTITLE_HEIGHT, EDIT_ITEMS,
                WaypointFormLayout.buttonsWidth(52, 52, 52), BUTTON_HEIGHT, statusHeight);
    }
}
