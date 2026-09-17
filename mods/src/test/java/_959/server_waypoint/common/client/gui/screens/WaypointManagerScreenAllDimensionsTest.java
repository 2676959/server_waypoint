package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void dimensionRefreshPreservesSelectionByName() {
        assertEquals(
                "minecraft:the_nether",
                WaypointManagerScreen.resolveSelectedDimension(
                        "minecraft:the_nether",
                        "minecraft:overworld",
                        List.of("minecraft:overworld", "minecraft:the_nether")
                )
        );
    }

    @Test
    void missingSelectionFallsBackToCurrentDimension() {
        assertEquals(
                "minecraft:overworld",
                WaypointManagerScreen.resolveSelectedDimension(
                        "removed:dimension",
                        "minecraft:overworld",
                        List.of("minecraft:overworld", "minecraft:the_nether")
                )
        );
    }

    @Test
    void missingSelectionAndCurrentDimensionFallBackToFirstAvailableDimension() {
        assertEquals(
                "example:first",
                WaypointManagerScreen.resolveSelectedDimension(
                        "removed:dimension",
                        "missing:current",
                        List.of("example:first", "example:second")
                )
        );
    }

    @Test
    void emptyDimensionListClearsSelection() {
        assertNull(WaypointManagerScreen.resolveSelectedDimension(
                "minecraft:overworld",
                "minecraft:overworld",
                List.of()
        ));
    }
}
