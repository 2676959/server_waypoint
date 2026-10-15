package _959.server_waypoint.common.client.gui.screens;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sizes are those the design was checked at: 480×270, the 378×245 of a Retina MacBook and the
 * 320×240 minimum. The theme dropdown is 120 pixels wide, and the footer's buttons are 162 pixels wide
 * together and 11 high, which leaves a status 130 pixels beside them at 320 pixels.
 */
class WidgetThemeEditorLayoutTest {
    private static final IntUnaryOperator NO_STATUS = width -> 0;

    @Test
    void groupColumnsAndPanelsMatchTheSpecAt480By270() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 270, 120, 162, 11, NO_STATUS);
        assertEquals(new WidgetThemeEditorLayout.Rect(30, 10, 420, 250), a.group());
        assertEquals(new WidgetThemeEditorLayout.Rect(30, 27, 210, 161), a.keyList());
        assertEquals(new WidgetThemeEditorLayout.Rect(30, 190, 210, 53), a.editor());
        assertEquals(new WidgetThemeEditorLayout.Rect(242, 27, 208, 216), a.preview());
        assertEquals(new WidgetThemeEditorLayout.Rect(288, 249, 162, 11), a.buttons());
    }

    @Test
    void columnsMatchTheSpecAt378By245And320By240() {
        WidgetThemeEditorLayout.Arrangement retina = WidgetThemeEditorLayout.arrange(378, 245, 120, 162, 11, NO_STATUS);
        assertEquals(179, retina.keyList().width());
        assertEquals(177, retina.preview().width());
        assertEquals(191, retina.preview().height());
        WidgetThemeEditorLayout.Arrangement minimum = WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, NO_STATUS);
        assertEquals(152, minimum.keyList().width());
        assertEquals(146, minimum.preview().width());
        assertEquals(186, minimum.preview().height());
    }

    @Test
    void theEditorSitsTwoPixelsUnderTheListAndEndsWithThePreview() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(378, 245, 120, 162, 11, NO_STATUS);
        assertEquals(a.keyList().bottom() + 2, a.editor().y());
        assertEquals(a.preview().bottom(), a.editor().bottom());
        assertEquals(a.keyList().right() + 2, a.preview().x());
    }

    @Test
    void titleLeavesTheDropdownItsWidthAndAnEightPixelGap() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 270, 120, 162, 11, NO_STATUS);
        assertEquals(292, a.title().width());
        assertEquals(a.group().right() - 120, a.dropdown().x());
    }

    @Test
    void aDropdownWiderThanTheGroupLeavesTheTitleNoWidth() {
        assertEquals(0, WidgetThemeEditorLayout.arrange(320, 240, 330, 162, 11, NO_STATUS).title().width());
    }

    @Test
    void statusSitsBesideTheButtonsWithRoomAndAboveThemOtherwise() {
        WidgetThemeEditorLayout.Arrangement beside = WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, width -> 9);
        assertFalse(beside.statusAbove());
        assertEquals(130, beside.status().width());
        WidgetThemeEditorLayout.Arrangement above = WidgetThemeEditorLayout.arrange(320, 240, 120, 200, 11, width -> 18);
        assertTrue(above.statusAbove());
        assertEquals(300, above.status().width());
        assertEquals(164, above.preview().height()); // 240 − 20 − 11 − 12 − (18 + 4 + 11)
    }

    @Test
    void panelsStopAt230AndTheGroupCentersOnTallScreens() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 400, 120, 162, 11, NO_STATUS);
        assertEquals(230, a.preview().height());
        assertEquals(68, a.group().y());
    }

    @Test
    void aShortScreenKeepsTheMinimumsAndStartsAtTheTopMargin() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(320, 150, 120, 162, 11, NO_STATUS);
        assertEquals(115, a.preview().height());
        assertEquals(10, a.group().y());
    }

    @Test
    void leftColumnIsHalfTheGroupBetween152And210() {
        assertEquals(210, WidgetThemeEditorLayout.leftColumnWidth(420));
        assertEquals(179, WidgetThemeEditorLayout.leftColumnWidth(358));
        assertEquals(152, WidgetThemeEditorLayout.leftColumnWidth(300));
    }

    @Test
    void valuesShowOnlyWhenTheWidestKeyAndAValueFit() {
        assertTrue(WidgetThemeEditorLayout.showsValues(190, 120, 46));
        assertFalse(WidgetThemeEditorLayout.showsValues(189, 120, 46));
    }

    @Test
    void theTitleAndTheDropdownFillTheHeaderLineAtTheTopOfTheGroup() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(480, 270, 120, 162, 11, NO_STATUS);
        assertEquals(new WidgetThemeEditorLayout.Rect(30, 10, 292, 11), a.title());
        assertEquals(new WidgetThemeEditorLayout.Rect(330, 10, 120, 11), a.dropdown());
    }

    @Test
    void aStatusShorterThanTheButtonsIsCenteredBesideThem() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, width -> 9);
        int footerTop = a.preview().bottom() + 6;
        assertEquals(new WidgetThemeEditorLayout.Rect(10, footerTop + 1, 130, 9), a.status());
        assertEquals(new WidgetThemeEditorLayout.Rect(148, footerTop, 162, 11), a.buttons());
    }

    @Test
    void aStatusTallerThanTheButtonsSetsTheFooterHeightBesideThem() {
        // Two lines in the 130 pixels beside the buttons, but one in the 300 of the whole group.
        WidgetThemeEditorLayout.Arrangement a =
                WidgetThemeEditorLayout.arrange(320, 240, 120, 162, 11, width -> width < 200 ? 18 : 9);
        int footerTop = a.preview().bottom() + 6;
        assertEquals(179, a.preview().height()); // 240 − 20 − 11 − 12 − 18
        assertEquals(new WidgetThemeEditorLayout.Rect(10, footerTop, 130, 18), a.status());
        // The buttons' row fills the footer, so they sit centered beside the status's lines.
        assertEquals(new WidgetThemeEditorLayout.Rect(148, footerTop, 162, 18), a.buttons());
        assertEquals(a.group().bottom(), a.buttons().bottom());
    }

    @Test
    void aStatusAboveTheButtonsStartsAtTheFootersTopAndTheButtonsEndAtItsBottom() {
        // The status is measured at the group's full 300 pixels, where it takes 18; narrower, it would take 36.
        WidgetThemeEditorLayout.Arrangement a =
                WidgetThemeEditorLayout.arrange(320, 240, 120, 200, 11, width -> width < 300 ? 36 : 18);
        int footerTop = a.preview().bottom() + 6;
        assertEquals(new WidgetThemeEditorLayout.Rect(10, footerTop, 300, 18), a.status());
        assertEquals(new WidgetThemeEditorLayout.Rect(110, footerTop + 18 + 4, 200, 11), a.buttons());
        assertEquals(a.group().bottom(), a.buttons().bottom());
    }

    @Test
    void noStatusAddsNothingToTheFooterEvenWhereItWouldSitAbove() {
        WidgetThemeEditorLayout.Arrangement a = WidgetThemeEditorLayout.arrange(320, 240, 120, 200, 11, NO_STATUS);
        assertTrue(a.statusAbove());
        assertEquals(186, a.preview().height()); // 240 − 20 − 11 − 12 − 11
        assertEquals(new WidgetThemeEditorLayout.Rect(110, a.preview().bottom() + 6, 200, 11), a.buttons());
        assertEquals(0, a.status().height());
    }

    @Test
    void theLeftColumnRoundsHalfAPixelUp() {
        assertEquals(180, WidgetThemeEditorLayout.leftColumnWidth(359));
        assertEquals(153, WidgetThemeEditorLayout.leftColumnWidth(305));
    }

    @Test
    void everythingStaysInsideTheGroupAndTheGroupInsideTheMargins() {
        for (int[] size : new int[][] {{480, 270}, {378, 245}, {320, 240}}) {
            WidgetThemeEditorLayout.Arrangement a =
                    WidgetThemeEditorLayout.arrange(size[0], size[1], 120, 162, 11, width -> 9);
            WidgetThemeEditorLayout.Rect margins =
                    new WidgetThemeEditorLayout.Rect(10, 10, size[0] - 20, size[1] - 20);
            String at = size[0] + "x" + size[1];
            assertTrue(inside(a.group(), margins), at + " " + a.group());
            List<WidgetThemeEditorLayout.Rect> parts =
                    List.of(a.title(), a.dropdown(), a.keyList(), a.editor(), a.preview(), a.buttons(), a.status());
            for (WidgetThemeEditorLayout.Rect part : parts) {
                assertTrue(inside(part, a.group()), at + " " + part);
            }
        }
    }

    private static boolean inside(WidgetThemeEditorLayout.Rect inner, WidgetThemeEditorLayout.Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
