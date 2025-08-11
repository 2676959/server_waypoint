package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.core.network.buffer.HandshakeBuffer;
import _959.server_waypoint.core.network.codec.HandshakeBufferCodec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;

public record HandshakeC2SPayload(HandshakeBuffer handshakeBuffer) implements CustomPayload {
    public static final Identifier HANDSHAKE_PAYLOAD_ID = Identifier.of(GROUP_ID, "handshake");
    public static final CustomPayload.Id<HandshakeC2SPayload> ID = new CustomPayload.Id<>(HANDSHAKE_PAYLOAD_ID);
    private static final PacketCodec<ByteBuf, HandshakeBuffer> CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, HandshakeBuffer value) {
            HandshakeBufferCodec.encode(buf, value);
        }

        @Override
        public HandshakeBuffer decode(ByteBuf buf) {
            return HandshakeBufferCodec.decode(buf);
        }
    };
    public static final PacketCodec<PacketByteBuf, HandshakeC2SPayload> PACKET_CODEC = PacketCodec.tuple(
            CODEC, HandshakeC2SPayload::handshakeBuffer,
            HandshakeC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
