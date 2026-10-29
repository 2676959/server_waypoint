package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.*;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import java.nio.file.Path;

final class WaypointFormTestAccess {
    static final ClientWaypointCollections COLLECTIONS = new ClientWaypointCollections(new WaypointFilesManagerCore());
    static final ClientWaypointAccess ACCESS = access(COLLECTIONS);
    static ClientWaypointAccess access(ClientWaypointCollections collections) {
        return new ClientWaypointAccess(collections, new LocalWaypointProfiles(Path.of("."), collections), new ClientWaypointAccess.Transport() {
                public boolean connected() { return false; }
                public String currentDimension() { return "minecraft:overworld"; }
                public int commandLimit() { return 32767; }
                public java.util.List<String> dimensions() { return java.util.List.of("minecraft:overworld"); }
                public boolean allowed(ClientWaypointSource source, ClientWaypointAccess.Action action) { return false; }
                public boolean command(String command) { throw new AssertionError("Unexpected command"); }
                public boolean edit(WaypointEditRequestMessage message) { throw new AssertionError("Unexpected edit"); }
            });
    }
    static ClientWaypointSelection selection() {
        return COLLECTIONS.selection(ClientWaypointSource.SHARED, "minecraft:overworld", "Base", null);
    }
}
