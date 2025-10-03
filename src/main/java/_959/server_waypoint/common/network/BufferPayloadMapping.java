package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.core.network.buffer.*;
import net.minecraft.network.packet.CustomPayload;

public class BufferPayloadMapping {
    public static CustomPayload getPayload(MessageBuffer packet) {
        return switch (packet.getChannelId()) {
            case WAYPOINT_LIST_CHANNEL -> new WaypointListS2CPayload((WaypointListBuffer) packet);
            case DIMENSION_WAYPOINT_CHANNEL -> new DimensionWaypointS2CPayload((DimensionWaypointBuffer) packet);
            case WORLD_WAYPOINT_CHANNEL -> new WorldWaypointS2CPayload((WorldWaypointBuffer) packet);
            case WAYPOINT_MODIFICATION_CHANNEL -> new WaypointModificationS2CPayload((WaypointModificationBuffer) packet);
            case HANDSHAKE_CHANNEL -> new HandshakeC2SPayload((HandshakeBuffer) packet);
            case XAEROS_WORLD_ID_CHANNEL -> new XaerosWorldIdS2CPayload((XaerosWorldIdBuffer) packet);
        };
    }
}
