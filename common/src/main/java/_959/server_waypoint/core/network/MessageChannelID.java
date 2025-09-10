package _959.server_waypoint.core.network;

import static _959.server_waypoint.core.WaypointServerCore.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.*;

public enum MessageChannelID {
    WAYPOINT_LIST_CHANNEL(WAYPOINT_LIST),
    DIMENSION_WAYPOINT_CHANNEL(DIMENSION_WAYPOINT),
    WORLD_WAYPOINT_CHANNEL(WORLD_WAYPOINT),
    WAYPOINT_MODIFICATION_CHANNEL(WAYPOINT_MODIFICATION),
    HANDSHAKE_CHANNEL(HANDSHAKE);

    private final String ID;

    MessageChannelID(String packetId) {
        this.ID = GROUP_ID + ":" + packetId;
    }

    @Override
    public String toString() {
        return this.ID;
    }
}