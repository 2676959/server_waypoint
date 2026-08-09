package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadCoordinatorTest {
    @Test
    void sameXaeroPropertiesIgnoreServerOnlyMetadata() {
        SimpleWaypoint server = new SimpleWaypoint(
                "home", "Main Home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false,
                List.of("base", "safe"), "The main storage base"
        );
        SimpleWaypoint uploaded = new SimpleWaypoint(
                "home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false
        );

        assertTrue(UploadCoordinator.hasSameXaeroProperties(server, uploaded));
    }

    @Test
    void mergeXaeroPropertiesPreservesServerOnlyMetadata() {
        SimpleWaypoint server = new SimpleWaypoint(
                "home", "Main Home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false,
                List.of("base", "safe"), "The main storage base"
        );
        SimpleWaypoint uploaded = new SimpleWaypoint(
                "home", "MH", new WaypointPos(42, 70, 7), 0xFFAA00, -45, true
        );

        SimpleWaypoint merged = UploadCoordinator.mergeXaeroProperties(server, uploaded);

        assertEquals(uploaded.name(), merged.name());
        assertEquals(uploaded.initials(), merged.initials());
        assertEquals(uploaded.pos(), merged.pos());
        assertEquals(uploaded.rgb(), merged.rgb());
        assertEquals(uploaded.yaw(), merged.yaw());
        assertEquals(uploaded.global(), merged.global());
        assertEquals(server.displayName(), merged.displayName());
        assertEquals(server.keywords(), merged.keywords());
        assertEquals(server.description(), merged.description());
    }
}
