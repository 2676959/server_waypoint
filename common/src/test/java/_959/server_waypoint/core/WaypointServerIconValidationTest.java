package _959.server_waypoint.core;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointServerIconValidationTest {
    @TempDir
    Path directory;

    @Test
    void acceptsRegisteredItemsAndBuiltInImagesButReservesVoxelMapNamespace() {
        var server = new WaypointServerCore(directory) {
            @Override
            protected boolean isRegisteredIconItem(NamespacedId icon) {
                return Set.of("minecraft:diamond", "examplemod:gem", "voxelmap:unknown").contains(icon.toString());
            }
        };

        assertTrue(server.isWaypointIconValid(null));
        for (String icon : Set.of("minecraft:diamond", "examplemod:gem", "voxelmap:waypoint", "voxelmap:star")) {
            assertTrue(server.isWaypointIconValid(NamespacedId.parse(icon)), icon);
        }
        for (String icon : Set.of("minecraft:air", "minecraft:missing", "examplemod:missing", "voxelmap:unknown")) {
            assertFalse(server.isWaypointIconValid(NamespacedId.parse(icon)), icon);
        }
        assertFalse(server.isWaypointIconValid(NamespacedId.parse("examplemod:" + "a".repeat(256))));
    }
}
