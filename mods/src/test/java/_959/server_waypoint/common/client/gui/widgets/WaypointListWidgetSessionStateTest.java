package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointListWidgetSessionStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @BeforeEach
    void resetBeforeTest() {
        WaypointListWidget.resetSessionStates();
    }

    @AfterEach
    void resetAfterTest() {
        WaypointListWidget.resetSessionStates();
    }

    @Test
    void allDimensionsScrollAndExpansionStateAreRetainedTogether() {
        WaypointListWidget.rememberSessionScrollPosition(true, 45.0D);
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        assertEquals(45.0D, WaypointListWidget.getSessionScrollPosition());
        assertFalse(WaypointListWidget.isDimensionExpanded(OVERWORLD));
    }

    @Test
    void selectedDimensionScrollIsNotRetained() {
        WaypointListWidget.rememberSessionScrollPosition(false, 45.0D);

        assertEquals(0.0D, WaypointListWidget.getSessionScrollPosition());
    }

    @Test
    void sessionResetClearsScrollAndExpansionState() {
        WaypointListWidget.rememberSessionScrollPosition(true, 45.0D);
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        WaypointListWidget.resetSessionStates();

        assertEquals(0.0D, WaypointListWidget.getSessionScrollPosition());
        assertTrue(WaypointListWidget.isDimensionExpanded(OVERWORLD));
    }

    @Test
    void expansionStateIsStoredPerDimension() {
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        assertFalse(WaypointListWidget.isDimensionExpanded(OVERWORLD));
        assertTrue(WaypointListWidget.isDimensionExpanded(NETHER));
    }
}
