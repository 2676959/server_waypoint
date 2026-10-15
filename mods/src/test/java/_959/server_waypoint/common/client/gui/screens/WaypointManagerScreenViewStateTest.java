package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaypointManagerScreenViewStateTest {
    @Test
    void integratedServerIsReadyInEveryNetworkState() {
        for (ClientNetworkState networkState : ClientNetworkState.values()) {
            assertEquals(
                    WaypointManagerScreen.ManagerViewState.READY,
                    WaypointManagerScreen.resolveViewState(true, networkState),
                    networkState.name()
            );
        }
    }

    @Test
    void dedicatedServerLoadsUntilSynchronizationFinishes() {
        assertEquals(WaypointManagerScreen.ManagerViewState.LOADING,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.NOT_READY));
        assertEquals(WaypointManagerScreen.ManagerViewState.LOADING,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.HANDSHAKE_FINISHED));
        assertEquals(WaypointManagerScreen.ManagerViewState.READY,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.SYNC_FINISHED));
    }

    @Test
    void unsupportedAndIncompatibleServersHaveTheirOwnStates() {
        assertEquals(WaypointManagerScreen.ManagerViewState.UNSUPPORTED,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.NO_SERVERSIDE_SUPPORT));
        assertEquals(WaypointManagerScreen.ManagerViewState.INCOMPATIBLE,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.INCOMPATIBLE_PROTOCOL));
    }

    @Test
    void onlyNonReadyStatesShowAMessage() {
        assertEquals("server_waypoint.manager.loading",
                WaypointManagerScreen.ManagerViewState.LOADING.messageKey());
        assertEquals("server_waypoint.no_serverside_support",
                WaypointManagerScreen.ManagerViewState.UNSUPPORTED.messageKey());
        assertEquals("server_waypoint.incompatible_protocol_version",
                WaypointManagerScreen.ManagerViewState.INCOMPATIBLE.messageKey());
        assertNull(WaypointManagerScreen.ManagerViewState.READY.messageKey());
    }
}
