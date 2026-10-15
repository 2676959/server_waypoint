package _959.server_waypoint.common.client.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsListLayoutTest {
    @Test
    void emptyColumnsTakeNoSpaceOrGap() {
        assertEquals(0, SettingsListLayout.suffixSlot(0));
        assertEquals(0, SettingsListLayout.actionSlot(0));
        assertEquals(38, SettingsListLayout.suffixSlot(35));
        assertEquals(17, SettingsListLayout.actionSlot(13));
    }

    @Test
    void labelGetsTheWidthLeftAfterTheControlTheColumnsAndTheGap() {
        // 300 - 135 control - 8 gap - 38 unit slot - 17 action slot
        assertEquals(102, SettingsListLayout.labelWidth(300, 135, 35, 13));
    }

    @Test
    void labelWidthNeverDropsBelowTheMinimum() {
        assertEquals(SettingsListLayout.MIN_LABEL_WIDTH, SettingsListLayout.labelWidth(120, 135, 35, 13));
    }

    @Test
    void preferredRowWidthFitsTheLabelOnOneLine() {
        // 105 label + 8 gap + 135 control + 38 unit slot + 17 action slot
        assertEquals(303, SettingsListLayout.rowPreferredWidth(105, 135, 35, 13));
    }

    @Test
    void rowsKeepTheirPaddingAndMinimumHeight() {
        assertEquals(21, SettingsListLayout.rowHeight(11));
        assertEquals(21, SettingsListLayout.rowHeight(13));
        assertEquals(26, SettingsListLayout.rowHeight(18));
        assertEquals(13, SettingsListLayout.headerHeight(9));
    }

    @Test
    void wideRowsKeepThreePixelsAboveAndBelowAndAtLeastSeventeen() {
        assertEquals(17, SettingsListLayout.wideRowHeight(9));
        assertEquals(17, SettingsListLayout.wideRowHeight(11));
        assertEquals(20, SettingsListLayout.wideRowHeight(14));
        assertEquals(26, SettingsListLayout.wideRowHeight(20));
    }

    @Test
    void headersAfterTheFirstEntryGetASectionGap() {
        int[] tops = SettingsListLayout.entryTops(
                new int[]{13, 21, 21, 13, 21},
                new boolean[]{true, false, false, true, false}
        );

        assertArrayEquals(new int[]{0, 13, 34, 61, 74, 95}, tops);
    }

    @Test
    void entryTopsRejectArraysOfDifferentLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> SettingsListLayout.entryTops(new int[]{1}, new boolean[0]));
    }

    @Test
    void fullVisibilityIncludesBothEdges() {
        assertTrue(SettingsListLayout.fullyVisible(10, 30, 10, 30));
        assertFalse(SettingsListLayout.fullyVisible(9, 30, 10, 30));
        assertFalse(SettingsListLayout.fullyVisible(10, 31, 10, 30));
    }

    @Test
    void revealKeepsTheScrollWhenTheRowAndItsNeighborsAreVisible() {
        assertEquals(10.0, SettingsListLayout.revealScroll(10.0, 100, 300, 40, 61, 19, 82));
    }

    @Test
    void revealScrollsDownJustEnoughToShowTheNextRow() {
        assertEquals(32.0, SettingsListLayout.revealScroll(0.0, 100, 300, 90, 111, 69, 132));
    }

    @Test
    void revealScrollsUpJustEnoughToShowThePreviousRow() {
        assertEquals(159.0, SettingsListLayout.revealScroll(200.0, 100, 400, 180, 201, 159, 222));
    }

    @Test
    void neighborsThatDoNotFitGiveWayToTheFocusedRow() {
        assertEquals(91.0, SettingsListLayout.revealScroll(0.0, 30, 400, 100, 121, 79, 142));
    }

    @Test
    void aRowTallerThanTheViewportShowsItsTop() {
        assertEquals(100.0, SettingsListLayout.revealScroll(0.0, 20, 400, 100, 150, 79, 171));
    }

    @Test
    void revealNeverScrollsPastTheContent() {
        assertEquals(50.0, SettingsListLayout.revealScroll(0.0, 100, 150, 120, 141, 99, 150));
    }

    @Test
    void revealingTheFirstRowShowsTheHeaderAboveIt() {
        assertEquals(0.0, SettingsListLayout.revealScroll(20.0, 100, 300, 13, 34, 0, 55));
    }
}
