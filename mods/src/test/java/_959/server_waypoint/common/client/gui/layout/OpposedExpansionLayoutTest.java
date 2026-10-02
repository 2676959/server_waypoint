package _959.server_waypoint.common.client.gui.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpposedExpansionLayoutTest {
    @Test void contentGrowsFromBothAnchorsWithoutFillingUnusedSpace() {
        assertEquals(new OpposedExpansionLayout.Sizes(52, 34), OpposedExpansionLayout.allocate(200, 16, 52, 34, 6));
    }

    @Test void shortRailGivesRemainingSpaceToLongRail() {
        assertEquals(new OpposedExpansionLayout.Sizes(16, 78), OpposedExpansionLayout.allocate(100, 16, 16, 300, 6));
        assertEquals(new OpposedExpansionLayout.Sizes(78, 16), OpposedExpansionLayout.allocate(100, 16, 300, 16, 6));
    }

    @Test void crowdedRailsShareSpaceAndKeepMinimumSizes() {
        assertEquals(new OpposedExpansionLayout.Sizes(47, 48), OpposedExpansionLayout.allocate(101, 16, 300, 300, 6));
        assertEquals(new OpposedExpansionLayout.Sizes(16, 16), OpposedExpansionLayout.allocate(38, 16, 0, 0, 6));
        assertEquals(new OpposedExpansionLayout.Sizes(0, 0), OpposedExpansionLayout.allocate(37, 16, 300, 300, 6));
    }
}
