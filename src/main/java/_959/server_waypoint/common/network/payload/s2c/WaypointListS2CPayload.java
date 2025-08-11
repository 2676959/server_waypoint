package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.WaypointPacketCodecs;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.waypoint.WaypointTypeID.WAYPOINT_LIST;

public record WaypointListS2CPayload(WaypointListBuffer waypointListBuffer) implements CustomPayload {
    public static final Identifier WAYPOINT_LIST_PAYLOAD_ID = Identifier.of(GROUP_ID, WAYPOINT_LIST);
    public static final CustomPayload.Id<WaypointListS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_LIST_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WaypointListS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WaypointPacketCodecs.WAYPOINT_LIST_BUFFER, WaypointListS2CPayload::waypointListBuffer,
            WaypointListS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}