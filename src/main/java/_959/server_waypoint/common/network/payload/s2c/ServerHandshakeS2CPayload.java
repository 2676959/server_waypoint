package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.PayloadID.SERVER_HANDSHAKE;

public record ServerHandshakeS2CPayload(ServerHandshakeBuffer serverHandshakeBuffer) implements CustomPayload {
    public static final Identifier SERVER_HANDSHAKE_PAYLOAD = Identifier.of(ModInfo.MOD_ID, SERVER_HANDSHAKE);
    public static final CustomPayload.Id<ServerHandshakeS2CPayload> ID = new CustomPayload.Id<>(SERVER_HANDSHAKE_PAYLOAD);
    private static final PacketCodec<ByteBuf, ServerHandshakeBuffer> CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, ServerHandshakeBuffer value) {
            ServerHandshakeCodec.encode(buf, value);
        }

        @Override
        public ServerHandshakeBuffer decode(ByteBuf buf) {
            return ServerHandshakeCodec.decode(buf);
        }
    };
    public static final PacketCodec<PacketByteBuf, ServerHandshakeS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            CODEC, ServerHandshakeS2CPayload::serverHandshakeBuffer,
            ServerHandshakeS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
