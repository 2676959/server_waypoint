//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VoxelMapUploadTest {
    @Test
    void collectsSyncedAndLocalWaypointsWithScaleAndColorConversion() {
        Waypoint local = waypoint("Local", 16, 70, -24, true, 0.5F, 0.25F, 1.0F);
        Waypoint synced = waypoint(
                SyncedWaypointName.format("Bases", "Home"),
                80,
                64,
                24,
                true,
                1.0F,
                0.0F,
                0.5F
        );

        DimensionWaypointData result = VoxelMapWaypointHelper.collectUploadDimension(
                request("minecraft:the_nether", null, null),
                "minecraft:the_nether",
                List.of(local, synced),
                ignored -> false,
                8.0
        );

        assertEquals("minecraft:the_nether", result.dimensionName());
        SimpleWaypoint localResult = list(result, "VoxelMap").simpleWaypoints().get(0);
        assertEquals("Local", localResult.name());
        assertEquals(2, localResult.x());
        assertEquals(70, localResult.y());
        assertEquals(-3, localResult.z());
        assertEquals(0x8040FF, localResult.rgb());
        assertEquals("", localResult.initials());
        assertEquals(0, localResult.yaw());
        assertFalse(localResult.global());

        SimpleWaypoint syncedResult = list(result, "Bases").simpleWaypoints().get(0);
        assertEquals("Home", syncedResult.name());
        assertEquals(10, syncedResult.x());
        assertEquals(3, syncedResult.z());
        assertEquals(0xFF0080, syncedResult.rgb());
    }

    @Test
    void appliesRequestFiltersAndSkipsDisabledAndCoordinateHighlights() {
        Waypoint keep = waypoint(
                SyncedWaypointName.format("Bases", "Keep"),
                1, 2, 3, true, 0.0F, 1.0F, 0.0F
        );
        Waypoint wrongWaypoint = waypoint(
                SyncedWaypointName.format("Bases", "Other"),
                1, 2, 3, true, 0.0F, 1.0F, 0.0F
        );
        Waypoint wrongList = waypoint("Keep", 1, 2, 3, true, 0.0F, 1.0F, 0.0F);
        Waypoint disabled = waypoint(
                SyncedWaypointName.format("Bases", "Keep"),
                1, 2, 3, false, 0.0F, 1.0F, 0.0F
        );
        Waypoint highlighted = waypoint(
                SyncedWaypointName.format("Bases", "Keep"),
                1, 2, 3, true, 0.0F, 1.0F, 0.0F
        );

        DimensionWaypointData result = VoxelMapWaypointHelper.collectUploadDimension(
                request("minecraft:overworld", "Bases", "Keep"),
                "minecraft:overworld",
                List.of(keep, wrongWaypoint, wrongList, disabled, highlighted),
                waypoint -> waypoint == highlighted,
                1.0
        );

        assertEquals(1, result.waypointLists().size());
        WaypointList bases = result.waypointLists().get(0);
        assertEquals("Bases", bases.name());
        assertEquals(List.of("Keep"), bases.simpleWaypoints().stream()
                .map(SimpleWaypoint::name)
                .toList());
    }

    @Test
    void includesRequestedDimensionWhenNoWaypointsMatch() {
        DimensionWaypointData result = VoxelMapWaypointHelper.collectUploadDimension(
                request("example:unvisited", null, null),
                "example:unvisited",
                List.of(),
                ignored -> false,
                1.0
        );

        assertEquals("example:unvisited", result.dimensionName());
        assertEquals(List.of(), result.waypointLists());
    }

    private static UploadRequestBuffer request(
            String dimensionName,
            String listName,
            String waypointName
    ) {
        return new UploadRequestBuffer(
                UUID.randomUUID(),
                List.of(dimensionName),
                listName,
                waypointName,
                UploadTarget.VOXELMAP
        );
    }

    private static Waypoint waypoint(
            String name,
            int x,
            int y,
            int z,
            boolean enabled,
            float red,
            float green,
            float blue
    ) {
        return new Waypoint(
                name, x, z, y, enabled, red, green, blue, "", "", new TreeSet<>()
        );
    }

    private static WaypointList list(DimensionWaypointData dimension, String name) {
        return dimension.waypointLists().stream()
                .filter(waypointList -> waypointList.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
//?}
