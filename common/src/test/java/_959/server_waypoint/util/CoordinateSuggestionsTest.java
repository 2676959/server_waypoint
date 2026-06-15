package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateSuggestionsTest {
    @Test
    void suggestsShortcutsWhenNoBlockIsTargeted() {
        assertEquals(List.of("~", "^"), CoordinateSuggestions.forAxis(CoordinateSuggestions.Axis.X, null));
    }

    @Test
    void suggestsTargetedBlockCoordinateForEachAxisBeforeShortcuts() {
        WaypointPos targetedBlock = new WaypointPos(12, 63, -8);

        assertEquals(List.of("12", "~", "^"), CoordinateSuggestions.forAxis(CoordinateSuggestions.Axis.X, targetedBlock));
        assertEquals(List.of("63", "~", "^"), CoordinateSuggestions.forAxis(CoordinateSuggestions.Axis.Y, targetedBlock));
        assertEquals(List.of("-8", "~", "^"), CoordinateSuggestions.forAxis(CoordinateSuggestions.Axis.Z, targetedBlock));
    }
}
