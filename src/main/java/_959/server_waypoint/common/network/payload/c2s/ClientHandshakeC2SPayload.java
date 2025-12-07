package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.CLIENT_HANDSHAKE;

public record ClientHandshakeC2SPayload(ClientHandshakeBuffer clientHandshakeBuffer) implements CustomPayload {
    public static final Identifier CLIENT_HANDSHAKE_PAYLOAD = Identifier.of(GROUP_ID, CLIENT_HANDSHAKE);
    public static final CustomPayload.Id<ClientHandshakeC2SPayload> ID = new CustomPayload.Id<>(CLIENT_HANDSHAKE_PAYLOAD);
    private static final PacketCodec<ByteBuf, ClientHandshakeBuffer> CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, ClientHandshakeBuffer value) {
            ClientHandshakeCodec.encode(buf);
        }

        @Override
        public ClientHandshakeBuffer decode(ByteBuf buf) {
            return ClientHandshakeCodec.decode(buf);
        }
    };
    public static final PacketCodec<PacketByteBuf, ClientHandshakeC2SPayload> PACKET_CODEC = PacketCodec.tuple(
            CODEC, ClientHandshakeC2SPayload::clientHandshakeBuffer,
            ClientHandshakeC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
