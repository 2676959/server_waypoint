package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.WaypointPacketCodecs;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.WORLD_WAYPOINT;


public record WorldWaypointS2CPayload(WorldWaypointBuffer worldWaypointBuffer) implements CustomPayload {
    public static final Identifier WORLD_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, WORLD_WAYPOINT);
    public static final CustomPayload.Id<WorldWaypointS2CPayload> ID = new CustomPayload.Id<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WaypointPacketCodecs.WORLD_WAYPOINT_BUFFER, WorldWaypointS2CPayload::worldWaypointBuffer,
            WorldWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
