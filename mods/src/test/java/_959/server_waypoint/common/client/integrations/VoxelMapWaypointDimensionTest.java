//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VoxelMapWaypointDimensionTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void netherWaypointIsInactiveInOverworld() throws ReflectiveOperationException {
        assertVisibility("minecraft:overworld", "minecraft:the_nether", "the_nether", false);
    }

    @Test
    void overworldWaypointIsInactiveInNether() throws ReflectiveOperationException {
        assertVisibility("minecraft:the_nether", "minecraft:overworld", "overworld", false);
    }

    @Test
    void currentDimensionWaypointRemainsActive() throws ReflectiveOperationException {
        assertVisibility("minecraft:the_nether", "minecraft:the_nether", "the_nether", true);
    }

    @Test
    void waypointIsInactiveWithoutACurrentDimension() throws ReflectiveOperationException {
        assertVisibility(null, "minecraft:overworld", "overworld", false);
    }

    private static void assertVisibility(String currentDimension, String waypointDimension,
                                         String storageName, boolean expectedActive)
            throws ReflectiveOperationException {
        // Use the real VoxelMap dimension lookup and waypoint constructor without initializing its HUD.
        VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
        Field dimensionManager = accessibleField(VoxelMap.class, "dimensionManager");
        Object previousDimensionManager = dimensionManager.get(voxelMap);
        Field currentDimensionName = accessibleField(WaypointClientMod.class, "currentDimensionName");
        Object previousDimensionName = currentDimensionName.get(null);
        dimensionManager.set(voxelMap, new DimensionManager());
        currentDimensionName.set(null, currentDimension);
        try {
            WaypointManager manager = allocate(WaypointManager.class);
            accessibleField(WaypointManager.class, "currentSubworldDescriptorNoCodes").set(manager, "test");
            Method convert = VoxelMapWaypointHelper.class.getDeclaredMethod("toVoxelMapWaypoint",
                    WaypointManager.class, String.class, String.class, SimpleWaypoint.class);
            convert.setAccessible(true);
            Waypoint waypoint = (Waypoint) convert.invoke(null, manager, waypointDimension, "Bases",
                    new SimpleWaypoint("Home", "H", 10, 64, 20, 0x123456, 0, false));

            assertNotNull(waypoint);
            assertEquals(1, waypoint.dimensions.size());
            assertEquals(storageName, waypoint.dimensions.first().getStorageName());
            assertEquals(expectedActive, waypoint.inDimension);
            assertEquals(expectedActive, waypoint.isActive());
        } finally {
            dimensionManager.set(voxelMap, previousDimensionManager);
            currentDimensionName.set(null, previousDimensionName);
        }
    }

    private static Field accessibleField(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Object unsafe = accessibleField(unsafeClass, "theUnsafe").get(null);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }
}
//?}
