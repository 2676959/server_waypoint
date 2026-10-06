//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void partialSyncContinuesPastSkippedWaypointsAndListsWithoutReportingFullSuccess()
            throws ReflectiveOperationException {
        VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
        Field dimensionManager = accessibleField(VoxelMap.class, "dimensionManager");
        Object previous = dimensionManager.get(voxelMap);
        dimensionManager.set(voxelMap, new DimensionManager());
        try {
            TestWaypointManager manager = allocate(TestWaypointManager.class);
            manager.waypoints = new ArrayList<>();
            Method addLists = VoxelMapWaypointHelper.class.getDeclaredMethod("addLists",
                    WaypointManager.class, String.class, List.class);
            addLists.setAccessible(true);

            assertEquals(false, addLists.invoke(null, manager, "minecraft:overworld", List.of(
                    new WaypointList("Bases", 1, List.of(simpleWaypoint("ambiguous\u241Fname"), simpleWaypoint("Home"))),
                    new WaypointList("ambiguous\u241Flist", 1, List.of()),
                    new WaypointList("Mines", 1, List.of(simpleWaypoint("Mine"))))));
            assertEquals(List.of("sw\u241FBases\u241FHome", "sw\u241FMines\u241FMine"),
                    manager.waypoints.stream().map(waypoint -> waypoint.name).toList());

            Method remove = VoxelMapWaypointHelper.class.getDeclaredMethod("removeSyncedWaypoint",
                    WaypointManager.class, String.class, String.class, String.class);
            remove.setAccessible(true);
            assertEquals(false, remove.invoke(null, manager, "minecraft:the_nether", "Bases", "Home"));
            assertEquals(true, remove.invoke(null, manager, "minecraft:overworld", "Bases", "Home"));
            assertEquals(false, remove.invoke(null, manager, "minecraft:overworld", "Bases", "Home"));
            assertEquals("sw\u241FMines\u241FMine", manager.waypoints.get(0).name);
        } finally {
            dimensionManager.set(voxelMap, previous);
        }
    }

    private static SimpleWaypoint simpleWaypoint(String name) {
        return new SimpleWaypoint(name, "H", 10, 64, 20, 0x123456, 0, false);
    }

    private static final class TestWaypointManager extends WaypointManager {
        private ArrayList<Waypoint> waypoints;

        @Override
        public ArrayList<Waypoint> getWaypoints() {
            return waypoints;
        }

        @Override
        public void addWaypoint(Waypoint waypoint) {
            waypoints.add(waypoint);
        }

        @Override
        public void deleteWaypoint(Waypoint waypoint) {
            waypoints.remove(waypoint);
        }

        @Override
        public String getCurrentSubworldDescriptor(boolean withCodes) {
            return "test";
        }
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
