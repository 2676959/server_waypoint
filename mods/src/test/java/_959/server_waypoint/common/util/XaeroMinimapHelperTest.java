package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class XaeroMinimapHelperTest {
    private static final String DEFAULT_SET = "gui.xaero_default";
    private static final String SYNCED_SET = "sw\u241Ftest";
    private static final String SYNCED_WAYPOINT = "sw\u241Fserver waypoint";

    @Test
    void removingSyncedWaypointSetPreservesUnmanagedWaypoints() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        WaypointSet waypointSet = WaypointSet.Builder.begin().setName(SYNCED_SET).build();
        waypointSet.add(createWaypoint(SYNCED_WAYPOINT));
        waypointSet.add(createWaypoint("local waypoint"));
        minimapWorld.addWaypointSet(waypointSet);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertEquals(waypointSet, minimapWorld.getWaypointSet(SYNCED_SET));
        assertEquals(SYNCED_SET, minimapWorld.getCurrentWaypointSetId());
        assertEquals(1, waypointSet.size());
        assertEquals("local waypoint", waypointSet.get(0).getName());
    }

    @Test
    void removingEmptySyncedWaypointSetSelectsAnExistingSet() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        minimapWorld.addWaypointSet(DEFAULT_SET);
        minimapWorld.addWaypointSet(SYNCED_SET);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertNull(minimapWorld.getWaypointSet(SYNCED_SET));
        assertEquals(DEFAULT_SET, minimapWorld.getCurrentWaypointSetId());
        assertNotNull(minimapWorld.getCurrentWaypointSet());
    }

    @Test
    void removingAlreadyMissingCurrentWaypointSetRepairsSelection() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        minimapWorld.addWaypointSet(DEFAULT_SET);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertEquals(DEFAULT_SET, minimapWorld.getCurrentWaypointSetId());
        assertNotNull(minimapWorld.getCurrentWaypointSet());
    }

    @Test
    void replacingListRemovesEquivalentLocalWaypointCopy() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint waypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet localSet = WaypointSet.Builder.begin().setName("test").build();
        localSet.add(createWaypoint(waypoint, waypoint.name()));
        minimapWorld.addWaypointSet(localSet);
        minimapWorld.setCurrentWaypointSetId("test");

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(waypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertNull(minimapWorld.getWaypointSet("test"));
        WaypointSet syncedSet = minimapWorld.getWaypointSet("sw\u241Ftest");
        assertNotNull(syncedSet);
        assertEquals(1, syncedSet.size());
        assertEquals("sw\u241FSpawn", syncedSet.get(0).getName());
    }

    @Test
    void replacingListRemovesEquivalentUnmanagedWaypointFromSyncedSet() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint waypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet syncedSet = WaypointSet.Builder.begin().setName("sw\u241Ftest").build();
        syncedSet.add(createWaypoint(waypoint, waypoint.name()));
        minimapWorld.addWaypointSet(syncedSet);

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(waypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(1, syncedSet.size());
        assertEquals("sw\u241FSpawn", syncedSet.get(0).getName());
    }

    @Test
    void replacingListPreservesConflictingLocalWaypoint() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint localWaypoint = new SimpleWaypoint("Spawn", "S", 10, 2, 3, 0, 0, false);
        SimpleWaypoint serverWaypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet localSet = WaypointSet.Builder.begin().setName("test").build();
        localSet.add(createWaypoint(localWaypoint, localWaypoint.name()));
        minimapWorld.addWaypointSet(localSet);

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(serverWaypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(localSet, minimapWorld.getWaypointSet("test"));
        assertEquals(1, localSet.size());
        assertEquals(10, localSet.get(0).getX());
        assertNotNull(minimapWorld.getWaypointSet("sw\u241Ftest"));
    }

    private static MinimapWorld createMinimapWorld() throws ReflectiveOperationException {
        //? if >= 1.21.5 {
        Constructor<MinimapWorld> constructor = MinimapWorld.class.getDeclaredConstructor(
                MinimapWorldContainer.class,
                String.class,
                ResourceKey.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, "test", null);
        //?} else {
        /*return new MinimapWorld(null, "test", null) {
        };
        *///?}
    }

    private static Waypoint createWaypoint(String name) throws ReflectiveOperationException {
        return createWaypoint(new SimpleWaypoint(name, "", 0, 0, 0, 0, 0, false), name);
    }

    private static Waypoint createWaypoint(SimpleWaypoint waypoint, String name) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            TestWaypoint testWaypoint = (TestWaypoint) unsafeClass
                    .getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, TestWaypoint.class);
            testWaypoint.initialize(waypoint, name);
            return testWaypoint;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create a Xaero waypoint test double", e);
        }
    }

    private static final class TestWaypoint extends Waypoint {
        private SimpleWaypoint waypoint;

        private TestWaypoint() {
            super(0, 0, 0, "", "", 0);
        }

        private void initialize(SimpleWaypoint waypoint, String name) {
            this.waypoint = waypoint;
            setX(waypoint.pos().x());
            setY(waypoint.pos().y());
            setZ(waypoint.pos().z());
            setName(name);
            setYaw(waypoint.yaw());
            setPurpose(xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL);
        }

        @Override
        public String getInitials() {
            return waypoint.initials();
        }

        @Override
        public int getColor() {
            return _959.server_waypoint.util.ColorUtils.rgbToClosestColorIndex(waypoint.rgb());
        }

        @Override
        public boolean isGlobal() {
            return waypoint.global();
        }
    }
}
