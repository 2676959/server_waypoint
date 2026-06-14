package _959.server_waypoint.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XaerosMapHelperTest {
    @Test
    void resolvesUnknownWorldMapYToFallback() {
        assertEquals(72, XaerosMapHelper.resolveWorldMapRightClickY(32767, 72));
    }

    @Test
    void resolvesKnownWorldMapYAboveClickedBlock() {
        assertEquals(65, XaerosMapHelper.resolveWorldMapRightClickY(64, 72));
    }

    @Test
    void resolvesWorldMapWaypointYFromStoredCoordinateWhenIncluded() {
        assertEquals(64, XaerosMapHelper.resolveWorldMapWaypointY(true, 64, 72));
    }

    @Test
    void resolvesWorldMapWaypointYToFallbackWhenNotIncluded() {
        assertEquals(72, XaerosMapHelper.resolveWorldMapWaypointY(false, 64, 72));
    }
}
