package _959.server_waypoint.core.network;

import static _959.server_waypoint.core.WaypointServerCore.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.*;

public enum MessageChannelID {
    WAYPOINT_LIST_CHANNEL(WAYPOINT_LIST),
    DIMENSION_WAYPOINT_CHANNEL(DIMENSION_WAYPOINT),
    WORLD_WAYPOINT_CHANNEL(WORLD_WAYPOINT),
    WAYPOINT_MODIFICATION_CHANNEL(WAYPOINT_MODIFICATION),
    SERVER_HANDSHAKE_CHANNEL(SERVER_HANDSHAKE),
    CLIENT_HANDSHAKE_CHANNEL(CLIENT_HANDSHAKE),
    CLIENT_UPDATE_REQUEST_CHANNEL(CLIENT_UPDATE_REQUEST),
    UPDATES_BUNDLE_CHANNEL(UPDATES_BUNDLE),
    XAEROS_WORLD_ID_CHANNEL("xaerominimap", "main");

    private final String ID;

    MessageChannelID(String packetId) {
        this(GROUP_ID, packetId);
    }

    MessageChannelID(String namespace, String packetId) {
        this.ID = namespace + ":" + packetId;
    }

    @Override
    public String toString() {
        return this.ID;
    }
}