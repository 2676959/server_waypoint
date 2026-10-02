package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.ClientConfigSync.MapModRowState;
import _959.server_waypoint.common.client.gui.screens.ClientConfigSync.SyncBlocker;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen.ManagerViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigSyncTest {
    @Test
    void mapModRowsFollowSupportAndInstallation() {
        assertEquals(MapModRowState.HIDDEN, ClientConfigSync.resolveMapModRowState(false, false));
        assertEquals(MapModRowState.HIDDEN, ClientConfigSync.resolveMapModRowState(false, true));
        assertEquals(MapModRowState.NOT_INSTALLED, ClientConfigSync.resolveMapModRowState(true, false));
        assertEquals(MapModRowState.INSTALLED, ClientConfigSync.resolveMapModRowState(true, true));
    }

    @Test
    void noWorldWinsOverEveryOtherBlocker() {
        for (ManagerViewState state : ManagerViewState.values()) {
            assertEquals(SyncBlocker.NO_WORLD, ClientConfigSync.resolveSyncBlocker(false, state, false));
            assertEquals(SyncBlocker.NO_WORLD, ClientConfigSync.resolveSyncBlocker(false, state, true));
        }
    }

    @Test
    void waypointStatesBlockBeforeTheMapMod() {
        assertEquals(SyncBlocker.WAYPOINTS_LOADING,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.LOADING, false));
        assertEquals(SyncBlocker.NO_SERVERSIDE_SUPPORT,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.UNSUPPORTED, false));
        assertEquals(SyncBlocker.INCOMPATIBLE_SERVER,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.INCOMPATIBLE, false));
    }

    @Test
    void aMapModThatIsNotReadyBlocksLast() {
        assertEquals(SyncBlocker.MAP_MOD_LOADING,
                ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.READY, false));
    }

    @Test
    void aReadyMapModInAReadyWorldCanSync() {
        assertNull(ClientConfigSync.resolveSyncBlocker(true, ManagerViewState.READY, true));
    }

    @Test
    void onlyTheMapModBlockerNamesTheMapMod() {
        for (SyncBlocker blocker : SyncBlocker.values()) {
            assertEquals(blocker == SyncBlocker.MAP_MOD_LOADING, blocker.namesMapMod(), blocker.name());
        }
        assertEquals("server_waypoint.manager.loading", SyncBlocker.WAYPOINTS_LOADING.messageKey());
        assertTrue(SyncBlocker.NO_WORLD.messageKey().startsWith("server_waypoint.config.sync."));
        assertFalse(SyncBlocker.NO_SERVERSIDE_SUPPORT.namesMapMod());
    }
}
