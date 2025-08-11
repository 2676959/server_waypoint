package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.WaypointPacketCodecs;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.waypoint.WaypointTypeID.SIMPLE_WAYPOINT;

public record SimpleWaypointS2CPayload(SimpleWaypoint simpleWaypoint) implements CustomPayload {
    public static final Identifier WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, SIMPLE_WAYPOINT);
    public static final CustomPayload.Id<SimpleWaypointS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SimpleWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WaypointPacketCodecs.SIMPLE_WAYPOINT, SimpleWaypointS2CPayload::simpleWaypoint,
            SimpleWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
