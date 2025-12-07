package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.core.network.buffer.*;

public class BufferPayloadMapping {
    public static ModPayload getPayload(MessageBuffer packet) {
        return switch (packet.getChannelId()) {
            case WAYPOINT_LIST_CHANNEL -> new WaypointListS2CPayload((WaypointListBuffer) packet);
            case DIMENSION_WAYPOINT_CHANNEL -> new DimensionWaypointS2CPayload((DimensionWaypointBuffer) packet);
            case WORLD_WAYPOINT_CHANNEL -> new WorldWaypointS2CPayload((WorldWaypointBuffer) packet);
            case WAYPOINT_MODIFICATION_CHANNEL -> new WaypointModificationS2CPayload((WaypointModificationBuffer) packet);
            case SERVER_HANDSHAKE_CHANNEL -> new ServerHandshakeS2CPayload((ServerHandshakeBuffer) packet);
            case CLIENT_HANDSHAKE_CHANNEL -> new ClientHandshakeC2SPayload((ClientHandshakeBuffer) packet);
            case CLIENT_UPDATE_REQUEST_CHANNEL -> new UpdateRequestC2SPayload((ClientUpdateRequestBuffer) packet);
            case UPDATES_BUNDLE_CHANNEL -> new UpdatesBundleS2CPayload((UpdatesBundleBuffer) packet);
            case XAEROS_WORLD_ID_CHANNEL -> new XaerosWorldIdS2CPayload((XaerosWorldIdBuffer) packet);
        };
    }
}
