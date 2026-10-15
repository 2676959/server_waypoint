package _959.server_waypoint.common.util;

import org.junit.jupiter.api.Test;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncedWaypointHighlightTest {
    @Test
    void leavesPersonalAndMissingSetsUnhighlighted() {
        WaypointSet personalSet = WaypointSet.Builder.begin().setName("Bases").build();

        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(personalSet, null));
        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(null, null));
    }

    @Test
    void preservesDropdownSelectionAndHoverDistinctions() {
        assertEquals(0x7F0D47A1, SyncedWaypointHighlight.xaerosBackground(-939524096));
        assertEquals(0xC80D47A1, SyncedWaypointHighlight.xaerosBackground(-13487566));
        assertEquals(0xCC00BCD4, SyncedWaypointHighlight.xaerosBackground(-922757376));
        assertEquals(0xDD26C6DA, SyncedWaypointHighlight.xaerosBackground(-10496));
        assertEquals(0xFF123456, SyncedWaypointHighlight.xaerosBackground(0xFF123456));
    }
}
