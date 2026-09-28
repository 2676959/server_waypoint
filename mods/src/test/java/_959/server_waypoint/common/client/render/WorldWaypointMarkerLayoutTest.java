package _959.server_waypoint.common.client.render;

import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldWaypointMarkerLayoutTest {
    @Test
    void itemAndVoxelMapIconsStackAboveCenteredInitials() {
        for (WaypointIconRenderer.Kind kind : new WaypointIconRenderer.Kind[]{
                WaypointIconRenderer.Kind.ITEM, WaypointIconRenderer.Kind.VOXELMAP}) {
            assertEquals(16, WorldWaypointMarkerLayout.width(kind, 11));
            assertEquals(27, WorldWaypointMarkerLayout.height(kind, 9));
            assertEquals(18.0F, WorldWaypointMarkerLayout.initialsTop(kind));
            assertEquals(0.0F, WorldWaypointMarkerLayout.iconLeft(11));
            assertEquals(2.5F, WorldWaypointMarkerLayout.initialsLeft(11));
            assertEquals(20, WorldWaypointMarkerLayout.width(kind, 20));
            assertEquals(2.0F, WorldWaypointMarkerLayout.iconLeft(20));
            assertEquals(0.0F, WorldWaypointMarkerLayout.initialsLeft(20));
        }
    }

    @Test
    void eitherVisiblePartTriggersHoverButTheGapDoesNot() {
        for (WaypointIconRenderer.Kind kind : new WaypointIconRenderer.Kind[]{
                WaypointIconRenderer.Kind.ITEM, WaypointIconRenderer.Kind.VOXELMAP}) {
            assertTrue(WorldWaypointMarkerLayout.contains(kind,
                    11, 9, 100, 100, 1, 100, 90));
            assertTrue(WorldWaypointMarkerLayout.contains(kind,
                    11, 9, 100, 100, 1, 100, 100));
            assertFalse(WorldWaypointMarkerLayout.contains(kind,
                    11, 9, 100, 100, 1, 100, 94.5F));
            assertFalse(WorldWaypointMarkerLayout.contains(kind,
                    11, 9, 100, 100, 1, 92.5F, 100));
        }
    }

    @Test
    void expandedNameUsesTheOriginalMarkPosition() {
        assertEquals(77.5F, WorldWaypointMarkerLayout.markerTop(WaypointIconRenderer.Kind.ITEM,
                9, 100, 1));
        assertEquals(95.5F, WorldWaypointMarkerLayout.labelTop(WaypointIconRenderer.Kind.ITEM,
                9, 100, 1));
        assertEquals(95.5F, WorldWaypointMarkerLayout.labelTop(WaypointIconRenderer.Kind.INITIALS,
                9, 100, 1));
        assertEquals(77.5F, WorldWaypointMarkerLayout.markerTop(WaypointIconRenderer.Kind.VOXELMAP,
                9, 100, 1));
        assertEquals(95.5F, WorldWaypointMarkerLayout.labelTop(WaypointIconRenderer.Kind.VOXELMAP,
                9, 100, 1));
    }

    @Test
    void initialsFallbackKeepsItsOriginalSize() {
        assertEquals(11, WorldWaypointMarkerLayout.width(WaypointIconRenderer.Kind.INITIALS, 11));
        assertEquals(9, WorldWaypointMarkerLayout.height(WaypointIconRenderer.Kind.INITIALS, 9));
    }
}
