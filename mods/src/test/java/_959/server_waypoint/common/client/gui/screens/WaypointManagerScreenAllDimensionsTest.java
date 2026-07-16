package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenAllDimensionsTest {
    @Test
    void visibleDimensionListRemainsActiveForSelectingANewWaypointTarget() {
        assertTrue(WaypointManagerScreen.resolveDimensionListActive(true));
    }

    @Test
    void hiddenDimensionListRemainsInactive() {
        assertFalse(WaypointManagerScreen.resolveDimensionListActive(false));
    }

    @Test
    void allDimensionsModeRefreshesForAnyChangedDimension() {
        assertTrue(WaypointManagerScreen.shouldRefreshDimension(
                true,
                "minecraft:the_nether",
                "minecraft:overworld"
        ));
    }

    @Test
    void selectedDimensionModeOnlyRefreshesTheSelectedDimension() {
        assertTrue(WaypointManagerScreen.shouldRefreshDimension(
                false,
                "minecraft:overworld",
                "minecraft:overworld"
        ));
        assertFalse(WaypointManagerScreen.shouldRefreshDimension(
                false,
                "minecraft:the_nether",
                "minecraft:overworld"
        ));
    }
}
