package _959.server_waypoint.network.payload;

import _959.server_waypoint.ServerWaypoint;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SimpleWaypointS2CPayload(SimpleWaypoint simpleWaypoint) implements CustomPayload {
    public static final Identifier WAYPOINT_PAYLOAD_ID = Identifier.of(ServerWaypoint.MOD_ID, "waypoint");
    public static final CustomPayload.Id<SimpleWaypointS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SimpleWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            SimpleWaypoint.PACKET_CODEC, SimpleWaypointS2CPayload::simpleWaypoint,
            SimpleWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
