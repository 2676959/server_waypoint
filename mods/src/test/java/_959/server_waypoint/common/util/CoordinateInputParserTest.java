package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinateInputParserTest {

    @Test
    void resolvesAbsoluteAndRelativeCoordinates() {
        WaypointPos playerPos = new WaypointPos(10, 64, -5);
        WaypointPos defaultPos = new WaypointPos(1, 2, 3);

        WaypointPos resolved = CoordinateInputParser.resolve("12", "~-2", "~", playerPos, defaultPos, 0.0F, 0.0F);

        assertEquals(new WaypointPos(12, 62, -5), resolved);
    }

    @Test
    void resolvesLocalCoordinatesLikeMinecraftCommandOrder() {
        WaypointPos playerPos = new WaypointPos(10, 64, -5);

        WaypointPos resolved = CoordinateInputParser.resolve("^-2", "^1", "^4", playerPos, playerPos, 0.0F, 0.0F);

        assertEquals(new WaypointPos(8, 65, -1), resolved);
    }

    @Test
    void rejectsLocalCoordinatesMixedWithAbsoluteCoordinates() {
        WaypointPos playerPos = new WaypointPos(10, 64, -5);
        WaypointPos defaultPos = new WaypointPos(10, 64, -5);

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateInputParser.resolve("^-2", "64", "^", playerPos, defaultPos, 0.0F, 0.0F)
        );
    }

    @Test
    void rejectsLocalCoordinatesMixedWithRelativeCoordinates() {
        WaypointPos playerPos = new WaypointPos(10, 64, -5);
        WaypointPos defaultPos = new WaypointPos(10, 64, -5);

        assertThrows(IllegalArgumentException.class, () ->
                CoordinateInputParser.resolve("^-2", "~-1", "^", playerPos, defaultPos, 0.0F, 0.0F)
        );
    }
}
