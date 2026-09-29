package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointListWidgetLabelLayoutTest {
    @Test
    void emptyNamesKeepDistanceAtNormalScale() {
        assertEquals(1.0F, WaypointListWidget.resolveLabelScale(80, 0),
                "An empty name must not suppress the distance rendered at the same scale");
    }

    @Test
    void labelScalingStillFitsNamesAndSuppressesRowsWithoutSpace() {
        assertEquals(1.0F, WaypointListWidget.resolveLabelScale(80, 40));
        assertEquals(0.5F, WaypointListWidget.resolveLabelScale(80, 160));
        assertEquals(0.0F, WaypointListWidget.resolveLabelScale(0, 40));
        assertEquals(0.0F, WaypointListWidget.resolveLabelScale(0, 0));
    }

    @Test
    void gapShrinksAsTheRowNarrowsWithoutShrinkingShortNames() {
        var wide = WaypointListWidget.resolveNameDistanceLayout(35, 24, 24, 240, 183, 156, false);
        var narrow = WaypointListWidget.resolveNameDistanceLayout(35, 24, 24, 133, 76, 49, false);

        assertEquals(1.0F, wide.nameScale());
        assertEquals(1.0F, narrow.nameScale());
        assertTrue(wide.showDistance());
        assertTrue(narrow.showDistance());
        assertTrue(wide.distanceX() > narrow.distanceX());
        assertTrue(narrow.distanceX() >= 62, "Keep three pixels after the 24-pixel name");
        assertTrue(narrow.distanceX() + 24 <= 130);
    }

    @Test
    void hoverHidesOnlyTheDistanceThatWouldOverlapButtons() {
        var idle = WaypointListWidget.resolveNameDistanceLayout(35, 24, 24, 133, 76, 49, false);
        var hovered = WaypointListWidget.resolveNameDistanceLayout(35, 24, 24, 133, 76, 49, true);
        var wideHovered = WaypointListWidget.resolveNameDistanceLayout(35, 24, 24, 240, 183, 156, true);

        assertTrue(idle.showDistance());
        assertFalse(hovered.showDistance());
        assertTrue(wideHovered.showDistance());
        assertEquals(idle.distanceX(), hovered.distanceX());
    }

    @Test
    void longNamesKeepMinimumGapAndFitWithDistanceInsideTheRow() {
        for (int indent : new int[]{0, 20}) {
            int x = WaypointListWidget.waypointLabelX(indent);
            var layout = WaypointListWidget.resolveNameDistanceLayout(x, 180, 24, 127, 70, 43, false);
            assertTrue(layout.nameScale() > 0.0F);
            assertTrue(layout.showDistance());
            assertTrue(layout.distanceX() - x - Math.ceil(180 * layout.nameScale()) >= 3);
            assertTrue(layout.distanceX() + 24 <= 124);

            var hovered = WaypointListWidget.resolveNameDistanceLayout(x, 180, 24, 127, 70, 43, true);
            assertTrue(x + Math.ceil(180 * hovered.nameScale()) <= 67);
            assertFalse(hovered.showDistance());
        }
    }

    @Test
    void emptyNameStillShowsDistanceAndMissingDistanceDoesNotReserveSpace() {
        var empty = WaypointListWidget.resolveNameDistanceLayout(35, 0, 24, 133, 76, 49, true);
        assertEquals(1.0F, empty.nameScale());
        assertTrue(empty.showDistance());
        var noDistance = WaypointListWidget.resolveNameDistanceLayout(35, 90, 0, 133, 76, 49, false);
        assertEquals(1.0F, noDistance.nameScale());
        assertFalse(noDistance.showDistance());
    }

    @Test
    void distanceFitsExactlyBeforeButtonsButHidesWhenOnePixelTooWide() {
        assertTrue(WaypointListWidget.resolveNameDistanceLayout(35, 0, 24,
                133, 76, 49, true).showDistance());
        assertFalse(WaypointListWidget.resolveNameDistanceLayout(35, 0, 25,
                133, 76, 49, true).showDistance());
    }
}
