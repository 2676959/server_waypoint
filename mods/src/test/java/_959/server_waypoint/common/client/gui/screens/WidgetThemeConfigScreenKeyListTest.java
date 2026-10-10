package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeConfigScreenKeyListTest {
    private static final int ROW = 12;
    private static final WidgetThemeVariable[] KEYS = WidgetThemeVariable.values();

    @Test
    void aRowLeavesOutThePanelPaddingAndTheScrollbar() {
        WidgetThemeConfigScreen.KeyList list = list(new ArrayList<>(), new ArrayList<>());
        list.setVisualWidth(207);
        list.setVisualHeight(150);

        // 207 less 6 pixels of padding on each side, then the 3-pixel scrollbar: 31 keys overflow 138 pixels.
        assertEquals(192, list.rowWidth());
    }

    @Test
    void revealScrollsTheLeastThatShowsTheWholeRow() {
        WidgetThemeConfigScreen.KeyList list = list(new ArrayList<>(), new ArrayList<>());
        list.setVisualWidth(200);
        // Four rows in view.
        list.setVisualHeight(12 + 4 * ROW);

        list.reveal(KEYS[5]);
        assertEquals(6 * ROW - 4 * ROW, list.getScrollY(), "a row below the view ends at its bottom");

        list.reveal(KEYS[3]);
        assertEquals(2 * ROW, list.getScrollY(), "a row in view leaves the list where it is");

        list.reveal(KEYS[1]);
        assertEquals(ROW, list.getScrollY(), "a row above the view starts at its top");
    }

    @Test
    void upAndDownAreReportedWhileTheListIsActive() {
        List<Boolean> moves = new ArrayList<>();
        WidgetThemeConfigScreen.KeyList list = list(new ArrayList<>(), moves);

        assertTrue(list.keyPressed(InputConstants.KEY_DOWN, 0, 0));
        assertTrue(list.keyPressed(InputConstants.KEY_UP, 0, 0));
        assertFalse(list.keyPressed(InputConstants.KEY_LEFT, 0, 0));
        list.active = false;
        assertFalse(list.keyPressed(InputConstants.KEY_DOWN, 0, 0));

        assertEquals(List.of(true, false), moves);
    }

    @Test
    void aLeftClickOnARowReportsItsKey() {
        List<WidgetThemeVariable> clicks = new ArrayList<>();
        WidgetThemeConfigScreen.KeyList list = list(clicks, new ArrayList<>());
        list.setVisualWidth(200);
        list.setVisualHeight(12 + 4 * ROW);
        list.setPosition(16, 26);
        int thirdRowY = 26 + 2 * ROW + 5;

        assertTrue(list.mouseClicked(40, thirdRowY, InputConstants.MOUSE_BUTTON_LEFT));
        assertFalse(list.mouseClicked(40, thirdRowY, InputConstants.MOUSE_BUTTON_RIGHT));

        assertEquals(List.of(KEYS[2]), clicks);
    }

    @Test
    void valuesAreWrittenAsInWidgetThemeJson() {
        assertEquals("#0A0B0C0D", WidgetThemeConfigScreen.KeyList.valueText(0x0A0B0C0D));
        assertEquals("#D9262626", WidgetThemeConfigScreen.KeyList.valueText(0xD9262626));
    }

    @Test
    void theWidestKeyAndValueAreMeasuredAtTheRowScale() {
        TestFont font = new TestFont();

        // control.disabledBackground and control.selectedBackground have 26 characters: 156 pixels, 132.6 at 85%.
        assertEquals(133, WidgetThemeConfigScreen.KeyList.widestKeyWidth(font));
        // A # and eight digits: 54 pixels, 45.9 at 85%.
        assertEquals(46, WidgetThemeConfigScreen.KeyList.widestValueWidth(font));
    }

    private static WidgetThemeConfigScreen.KeyList list(List<WidgetThemeVariable> clicks, List<Boolean> moves) {
        return new WidgetThemeConfigScreen.KeyList(new TestFont(), clicks::add, moves::add);
    }
}
