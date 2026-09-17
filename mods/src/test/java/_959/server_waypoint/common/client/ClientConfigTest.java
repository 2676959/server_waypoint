package _959.server_waypoint.common.client;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    @Test
    void missingWaypointManagerStateUsesExistingDefaults() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);

        assertEquals(WaypointSorting.SortMode.DEFAULT, config.getWaypointManagerSortMode());
        assertFalse(config.isWaypointManagerSortReversed());
        assertTrue(config.isWaypointManagerGroupByLists());
        assertFalse(config.isWaypointManagerShowAllDimensions());
    }

    @Test
    void waypointManagerStateSurvivesJsonRoundTrip() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        config.setWaypointManagerSortMode(WaypointSorting.SortMode.DISTANCE);
        config.setWaypointManagerSortReversed(true);
        config.setWaypointManagerGroupByLists(false);
        config.setWaypointManagerShowAllDimensions(true);

        ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);

        assertEquals(WaypointSorting.SortMode.DISTANCE, restored.getWaypointManagerSortMode());
        assertTrue(restored.isWaypointManagerSortReversed());
        assertFalse(restored.isWaypointManagerGroupByLists());
        assertTrue(restored.isWaypointManagerShowAllDimensions());
    }

    @Test
    void defaultSortKeepsItsRequiredGroupedForwardState() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        config.setWaypointManagerSortMode(WaypointSorting.SortMode.NAME);
        config.setWaypointManagerSortReversed(true);
        config.setWaypointManagerGroupByLists(false);

        config.setWaypointManagerSortMode(WaypointSorting.SortMode.DEFAULT);

        assertFalse(config.isWaypointManagerSortReversed());
        assertTrue(config.isWaypointManagerGroupByLists());
    }
}
