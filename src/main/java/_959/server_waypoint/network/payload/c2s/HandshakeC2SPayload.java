package _959.server_waypoint.network.payload.c2s;

import _959.server_waypoint.ServerWaypoint;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HandshakeC2SPayload(int waypointsEdition) implements CustomPayload {
    public static final Identifier HANDSHAKE_PAYLOAD_ID = Identifier.of(ServerWaypoint.MOD_ID, "handshake");
    public static final CustomPayload.Id<HandshakeC2SPayload> ID = new CustomPayload.Id<>(HANDSHAKE_PAYLOAD_ID);
    public static final PacketCodec<PacketByteBuf, HandshakeC2SPayload> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, HandshakeC2SPayload::waypointsEdition,
            HandshakeC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
