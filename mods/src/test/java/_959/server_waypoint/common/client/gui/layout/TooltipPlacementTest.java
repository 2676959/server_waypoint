package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipPlacementTest {
    private static final int W = 320;
    private static final int H = 240;
    /** Left 100, top 50, right 140, bottom 70. */
    private static final ScreenRectangle CONTROL = new ScreenRectangle(100, 50, 40, 20);

    private static TooltipPlacement.Position at(int x, int y) {
        return new TooltipPlacement.Position(x, y);
    }

    @Test
    void atPointerSitsRightOfAndAboveThePointer() {
        assertEquals(at(112, 88), TooltipPlacement.atPointer(W, H, 100, 100, 50, 8));
    }

    @Test
    void atPointerFlipsLeftAtTheRightEdge() {
        assertEquals(at(238, 88), TooltipPlacement.atPointer(W, H, 300, 100, 50, 8));
        assertEquals(at(4, 88), TooltipPlacement.atPointer(W, H, 40, 100, 300, 8));
    }

    @Test
    void atPointerStaysAboveTheBottomEdge() {
        assertEquals(at(112, 220), TooltipPlacement.atPointer(W, H, 100, 235, 50, 17));
    }

    @Test
    void atPointerKeepsTheBoxTopOnTheScreen() {
        assertEquals(at(112, 4), TooltipPlacement.atPointer(W, H, 100, 10, 50, 8));
    }

    @Test
    void besideControlGoesBelowItByWhereThePointerIs() {
        // y0 = my + 3. Pointer 10 pixels into the control: off = round(lerp(13 / 20, 17, 5)) = 9.
        assertEquals(at(122, 72), TooltipPlacement.besideControl(W, H, 110, 60, 50, 8, CONTROL));
        // Pointer on the top edge: off = round(lerp(3 / 20, 17, 5)) = 15.
        assertEquals(at(122, 68), TooltipPlacement.besideControl(W, H, 110, 50, 50, 8, CONTROL));
        // Pointer on the bottom edge: off = 5.
        assertEquals(at(122, 77), TooltipPlacement.besideControl(W, H, 110, 69, 50, 8, CONTROL));
    }

    @Test
    void besideControlGoesAboveItNearTheBottom() {
        ScreenRectangle low = new ScreenRectangle(100, 200, 40, 20); // bottom 220
        // 220 + 3 + 17 + (8 + 6) > 240 - 5, so above: y = 213 - 14 - round(lerp(7 / 20, 17, 5)) = 186.
        assertEquals(at(122, 186), TooltipPlacement.besideControl(W, H, 110, 210, 50, 8, low));
    }

    @Test
    void besideControlFlipsLeftAtTheRightEdge() {
        assertEquals(at(228, 72), TooltipPlacement.besideControl(W, H, 290, 60, 50, 8, CONTROL));
        assertEquals(at(9, 72), TooltipPlacement.besideControl(W, H, 30, 60, 300, 8, CONTROL));
    }

    @Test
    void besideControlKeepsTheBoxTopOnTheScreen() {
        ScreenRectangle control = new ScreenRectangle(100, 30, 40, 20); // bottom 50
        // A 60-pixel screen and a 26-pixel text block: above, y = 43 - 32 - 13 = -2.
        assertEquals(at(112, 4), TooltipPlacement.besideControl(W, 60, 100, 40, 50, 26, control));
    }

    @Test
    void besideControlCountsAZeroHeightControlAsOnePixelHigh() {
        ScreenRectangle flat = new ScreenRectangle(100, 50, 40, 0);
        // With height 1, off = round(lerp(min(3, 1) / 1, -2, 5)) = 5. Without the guard it would be 0.
        assertEquals(at(122, 58), TooltipPlacement.besideControl(W, H, 110, 50, 50, 8, flat));
    }

    @Test
    void belowOrAboveControlGoesBelowIt() {
        assertEquals(at(103, 74), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, CONTROL));
    }

    @Test
    void belowOrAboveControlGoesAboveItNearTheBottom() {
        ScreenRectangle low = new ScreenRectangle(100, 220, 40, 16); // bottom 236
        assertEquals(at(103, 208), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, low));
    }

    @Test
    void belowOrAboveControlShiftsLeftAtTheRightEdge() {
        ScreenRectangle right = new ScreenRectangle(290, 50, 20, 20); // right 310
        assertEquals(at(257, 74), TooltipPlacement.belowOrAboveControl(W, H, 50, 8, right));
        assertEquals(at(4, 74), TooltipPlacement.belowOrAboveControl(W, H, 330, 8, right));
    }

    @Test
    void belowOrAboveControlKeepsTheBoxTopOnTheScreen() {
        ScreenRectangle top = new ScreenRectangle(100, 10, 40, 16); // bottom 26
        assertEquals(at(103, 4), TooltipPlacement.belowOrAboveControl(W, 30, 50, 8, top));
    }
}
