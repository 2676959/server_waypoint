package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

import java.lang.reflect.Constructor;

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

    private static MinimapWorld createMinimapWorld() throws ReflectiveOperationException {
        Constructor<MinimapWorld> constructor = MinimapWorld.class.getDeclaredConstructor(
                MinimapWorldContainer.class,
                String.class,
                ResourceKey.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, "test", null);
    }

    private static Waypoint createWaypoint(String name) {
        SimpleWaypoint simpleWaypoint = new SimpleWaypoint("test", "T", 0, 64, 0, 0xFFFFFF, 0, false);
        return XaerosWaypointHelper.simpleWaypointToXaerosWaypoint(simpleWaypoint, name);
    }
}
