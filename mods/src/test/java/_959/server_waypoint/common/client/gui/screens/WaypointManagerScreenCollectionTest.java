package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.*;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WaypointManagerScreenCollectionTest {
    @Test
    void freshConfigCreatesLocallyAndSingleSourceDefaultsNeverFallBack() {
        var config = new Gson().fromJson("{}", ClientConfig.class);
        assertEquals(ClientWaypointSource.LOCAL, config.getWaypointManagerView().destination());
        for (var view : ClientWaypointQuery.View.values()) {
            config.setWaypointManagerView(view);
            var restored = new Gson().fromJson(new Gson().toJson(config), ClientConfig.class);
            assertEquals(view, restored.getWaypointManagerView());
            if (view != ClientWaypointQuery.View.COMBINED) assertEquals(view.name(), view.destination().name());
        }
    }

    @Test
    void combinedAndLocalRemainUsableForEveryServerNetworkState() {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore());
        collections.bindLocal(UUID.randomUUID(), new WaypointFilesManagerCore());
        for (var state : WaypointClientMod.ClientNetworkState.values()) {
            assertEquals(WaypointManagerScreen.ManagerViewState.READY,
                    WaypointManagerScreen.resolveViewState(ClientWaypointQuery.View.COMBINED, collections, false, state));
            assertEquals(WaypointManagerScreen.ManagerViewState.READY,
                    WaypointManagerScreen.resolveViewState(ClientWaypointQuery.View.LOCAL, collections, false, state));
        }
        assertEquals(WaypointManagerScreen.ManagerViewState.UNSUPPORTED,
                WaypointManagerScreen.resolveViewState(ClientWaypointQuery.View.SHARED, collections, false,
                        WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT));
    }
}
