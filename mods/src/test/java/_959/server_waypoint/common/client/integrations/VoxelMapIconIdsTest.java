package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoxelMapIconIdsTest {
    @Test
    void mapsOnlyRecognizedNativeSuffixes() {
        assertEquals(NamespacedId.parse("voxelmap:star"), VoxelMapIconIds.fromSuffix("star"));
        assertEquals(NamespacedId.parse("voxelmap:waypoint"), VoxelMapIconIds.fromSuffix(""));
        assertEquals("star", VoxelMapIconIds.toSuffix(NamespacedId.parse("voxelmap:star")));
        assertEquals("", VoxelMapIconIds.toSuffix(NamespacedId.parse("minecraft:diamond")));
        assertEquals("", VoxelMapIconIds.toSuffix(null));
        assertEquals("", VoxelMapIconIds.toSuffix(NamespacedId.parse("voxelmap:missing")));
        assertNull(VoxelMapIconIds.fromSuffix("../star"));
        assertNull(VoxelMapIconIds.fromSuffix("foo/bar"));
        assertNull(VoxelMapIconIds.fromSuffix("star:bad"));
    }
}
