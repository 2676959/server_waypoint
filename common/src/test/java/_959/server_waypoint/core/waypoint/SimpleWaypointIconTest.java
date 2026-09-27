package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.NamespacedId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleWaypointIconTest {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @Test
    void selectedIconSurvivesCopyAndJsonRoundTrip() {
        SimpleWaypoint selected = waypointWithIcon(NamespacedId.parse("voxelmap:star"));

        assertEquals(NamespacedId.parse("voxelmap:star"), new SimpleWaypoint(selected).icon());
        assertEquals("voxelmap:star", GSON.toJsonTree(selected).getAsJsonObject().get("icon").getAsString());
        assertEquals(selected.icon(), GSON.fromJson(GSON.toJson(selected), SimpleWaypoint.class).icon());
    }

    @Test
    void missingAndInvalidJsonIconUseInitialsFallback() {
        JsonObject json = GSON.toJsonTree(waypointWithIcon(null)).getAsJsonObject();
        json.remove("icon");
        assertNull(GSON.fromJson(json, SimpleWaypoint.class).icon());
        json.addProperty("icon", "bad%icon");
        assertNull(GSON.fromJson(json, SimpleWaypoint.class).icon());
        assertNull(GSON.toJsonTree(waypointWithIcon(null)).getAsJsonObject().get("icon"));
    }

    private static SimpleWaypoint waypointWithIcon(NamespacedId icon) {
        return new SimpleWaypoint("Home", "Home", "H", new WaypointPos(1, 2, 3),
                0xFFFFFF, 0, true, List.of(), "", icon);
    }
}
