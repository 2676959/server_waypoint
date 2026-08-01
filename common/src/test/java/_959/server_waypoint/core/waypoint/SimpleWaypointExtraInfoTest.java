package _959.server_waypoint.core.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleWaypointExtraInfoTest {
    @Test
    void identityNameIsTheDefaultDisplayName() {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Home",
                "H",
                new WaypointPos(1, 2, 3),
                0xFFFFFF,
                0,
                true
        );
        WaypointList waypointList = new WaypointList("Bases", 1, List.of(waypoint));

        assertEquals("Home", waypoint.displayName());
        assertEquals("Bases", waypointList.displayName());
        assertFalse(gson.toJsonTree(waypoint).getAsJsonObject().has("display_name"));
        assertFalse(gson.toJsonTree(waypointList).getAsJsonObject().has("display_name"));
    }

    @Test
    void serializesWaypointAndListDisplayNamesSeparatelyFromIdentityNames() {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        String waypointDisplayName = "{\"text\":\"Home\",\"color\":\"gold\"}";
        String listDisplayName = "{\"text\":\"Bases\",\"bold\":true}";
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Home",
                waypointDisplayName,
                "H",
                new WaypointPos(1, 2, 3),
                0xFFFFFF,
                0,
                true,
                List.of(),
                ""
        );
        WaypointList waypointList = new WaypointList(
                "Bases",
                listDisplayName,
                1,
                List.of(waypoint)
        );

        JsonObject waypointJson = gson.toJsonTree(waypoint).getAsJsonObject();
        JsonObject listJson = gson.toJsonTree(waypointList).getAsJsonObject();

        assertEquals("Home", waypointJson.get("name").getAsString());
        assertEquals(waypointDisplayName, waypointJson.get("display_name").getAsString());
        assertEquals("Bases", listJson.get("list_name").getAsString());
        assertEquals(listDisplayName, listJson.get("display_name").getAsString());
    }

    @Test
    void keywordSnapshotsAreImmutable() {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Home",
                "H",
                new WaypointPos(1, 2, 3),
                0xFFFFFF,
                0,
                true,
                List.of("base"),
                "description"
        );

        assertThrows(UnsupportedOperationException.class, () -> waypoint.keywords().add("other"));
    }
}
