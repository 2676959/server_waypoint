//? if voxelmap {
package _959.server_waypoint.common.client.integrations;

import com.mamiyaotaru.voxelmap.VoxelMapWorldFixture;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelMapReadinessTest {
    @BeforeAll
    static void requireClientWorldRuntime() throws ReflectiveOperationException {
        try {
            allocate(ClientLevel.class);
        } catch (ExceptionInInitializerError unavailableLoader) {
            if (unavailableLoader.getCause() instanceof IllegalStateException cause
                    && "There is no current FML Loader".equals(cause.getMessage())) {
                Assumptions.abort("ClientLevel requires a running NeoForge loader in this test runtime");
            }
            throw unavailableLoader;
        }
    }

    @Test
    void readinessRequiresTheCurrentWorldAndBothManagers() throws ReflectiveOperationException {
        ClientLevel current = allocate(ClientLevel.class);
        VoxelMapWorldFixture map = allocate(VoxelMapWorldFixture.class);
        map.loadedWorld = allocate(ClientLevel.class);
        map.waypointManager = allocate(WaypointManager.class);
        map.dimensionManager = new DimensionManager();

        assertFalse(VoxelMapWaypointHelper.isReady(map, current), "previous world is still loaded");
        map.loadedWorld = null;
        assertFalse(VoxelMapWaypointHelper.isReady(map, current), "world has not loaded yet");
        map.loadedWorld = current;
        assertTrue(VoxelMapWaypointHelper.isReady(map, current));
        assertFalse(VoxelMapWaypointHelper.isReady(map, null), "client has left the world");
        map.waypointManager = null;
        assertFalse(VoxelMapWaypointHelper.isReady(map, current));
        map.waypointManager = allocate(WaypointManager.class);
        map.dimensionManager = null;
        assertFalse(VoxelMapWaypointHelper.isReady(map, current));
        assertFalse(VoxelMapWaypointHelper.isReady(null, current));
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
}
//?}
