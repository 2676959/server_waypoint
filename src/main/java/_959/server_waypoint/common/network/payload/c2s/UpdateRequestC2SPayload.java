package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.core.network.buffer.ClientUpdateRequestBuffer;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.CLIENT_UPDATE_REQUEST;

public record UpdateRequestC2SPayload(ClientUpdateRequestBuffer clientUpdateRequestBuffer) implements CustomPayload {
    public static final Identifier CLIENT_UPDATE_REQUEST_PAYLOAD = Identifier.of(GROUP_ID, CLIENT_UPDATE_REQUEST);
    public static final CustomPayload.Id<UpdateRequestC2SPayload> ID = new CustomPayload.Id<>(CLIENT_UPDATE_REQUEST_PAYLOAD);
    private static final PacketCodec<ByteBuf, ClientUpdateRequestBuffer> CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, ClientUpdateRequestBuffer value) {
            ClientUpdateRequestBufferCodec.encode(buf, value);
        }

        @Override
        public ClientUpdateRequestBuffer decode(ByteBuf buf) {
            return ClientUpdateRequestBufferCodec.decode(buf);
        }
    };
    public static final PacketCodec<PacketByteBuf, UpdateRequestC2SPayload> PACKET_CODEC = PacketCodec.tuple(
            CODEC, UpdateRequestC2SPayload::clientUpdateRequestBuffer,
            UpdateRequestC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
