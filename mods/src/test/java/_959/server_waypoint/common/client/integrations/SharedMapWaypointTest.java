package _959.server_waypoint.common.client.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;

import static org.junit.jupiter.api.Assertions.*;

class SharedMapWaypointTest {
    @TempDir Path directory;

    @Test
    void encodedIdentityChecksOnlyItsOwnDimensionAndList() {
        WaypointFilesManagerCore files = new WaypointFilesManagerCore(this.directory);
        files.putWaypointList("minecraft:overworld", new WaypointList("Bases", 1, List.of(
                new SimpleWaypoint("Home", "H", new WaypointPos(1, 64, 3), 0x123456, 0, false))));
        SharedMapWaypoint same = parse("[name:sw␟Bases␟Home, x:99, z:99]");
        SharedMapWaypoint otherList = parse("[name:sw␟Mines␟Home, x:1, z:3]");
        SharedMapWaypoint otherDimension = parse("[name:sw␟Bases␟Home, x:1, z:3, dim:minecraft:the_nether]");
        assertTrue(same.isOnServer(files));
        assertFalse(otherList.isOnServer(files));
        assertFalse(otherDimension.isOnServer(files));
    }

    private SharedMapWaypoint parse(String message) {
        return SharedMapWaypoint.parse(message, "minecraft:overworld", 71);
    }

    @Test
    void voxelShareRestoresEncodedIdentityAndKeepsNativeDimensionCoordinates() {
        SharedMapWaypoint share = parse("[name:sw␟Bases␟Home﹐ ⟦west⟧, x:-12, y:64, z:9, dim:minecraft:the_nether, icon:star]");
        assertNotNull(share);
        assertEquals("sw␟Bases␟Home, [west]", share.name());
        assertEquals("minecraft:the_nether", share.dimensionName());
        assertEquals(-12, share.x());
        assertEquals("voxelmap:star", share.icon());
    }

    @Test
    void xaeroShareRestoresEscapesAndExplicitDimension() {
        SharedMapWaypoint share = parse("xaero-waypoint:Home-base^min^east^col^1:HB:-12:~:9:12:true:90:Internal-the-nether");
        assertNotNull(share);
        assertEquals("Home_base-east:1", share.name());
        assertEquals("minecraft:the_nether", share.dimensionName());
        assertEquals(71, share.y());
        assertEquals(0xFF0000, share.rgb());
        assertEquals(90, share.yaw());
    }

    @Test
    void xaeroDimensionEndingInUnderscoreAndDigitsIsPreserved() {
        var share = parse("xaero-waypoint:Home:H:1:64:3:12:false:0:Internal-dim%example$room-2");
        assertNotNull(share);
        assertEquals("example:room_2", share.dimensionName());
    }

    @Test
    void xaeroCustomDimensionRestoresDirectoryEscapes() {
        var share = parse("xaero-waypoint:Home:H:1:64:3:12:false:0:Internal-dim%example$rooms%base,,");
        assertNotNull(share);
        assertEquals("example:rooms/base..", share.dimensionName());
    }

    //? if <=1.21.3 {
    /*@Test
    void nativeXaeroDestinationUsesWaypointWorldNode() {
        var share = parse("xaero-waypoint:Home:H:1:64:3:12:false:0:Internal-the-nether-waypoints");
        assertNotNull(share);
        assertEquals("minecraft:the_nether", share.dimensionName());
        share = parse("xaero-waypoint:Home:H:1:64:3:12:false:0:Internal-waypoints");
        assertNotNull(share);
        assertEquals("minecraft:overworld", share.dimensionName());
    }
    *///?} else {
    @Test
    void modernXaeroDimensionEndingInWaypointTextIsPreserved() {
        var share = parse("xaero-waypoint:Home:H:1:64:3:12:false:0:Internal-dim%example$my-waypoints");
        assertNotNull(share);
        assertEquals("example:my_waypoints", share.dimensionName());
    }
    //?}

    @Test
    void ordinaryMalformedAndExternalSharesDoNotOfferServerAdds() {
        for (String message : new String[]{"hello", "[name:Home, x:NaN, z:3]",
                "[name:Home, x:1, z:3, dim:bad dimension]",
                "xaero-waypoint:Home:H:1:64:3:12:true:0:External",
                "xaero-waypoint:Home:H:1:64:3:99:true:0:Internal",
                "[name:Home, x:1, x:2, z:3]"}) {
            assertNull(parse(message), message);
        }
    }

    @Test
    void voxelCoordinateShareUsesCurrentDimensionAndFallbackHeight() {
        SharedMapWaypoint share = parse("[x:12, z:-9]");
        assertNotNull(share);
        assertEquals("minecraft:overworld", share.dimensionName());
        assertEquals(71, share.y());
    }
}
