package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.HandshakeBuffer;
import _959.server_waypoint.core.network.codec.HandshakeBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.HANDSHAKE;

public record HandshakeC2SPayload(HandshakeBuffer handshakeBuffer) implements ModPayload {
    public static final Identifier HANDSHAKE_PAYLOAD_ID = Identifier.of(GROUP_ID, HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<HandshakeC2SPayload> ID = new CustomPayload.Id<>(HANDSHAKE_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, HandshakeC2SPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, HandshakeC2SPayload value) {
            HandshakeBufferCodec.encode(buf, value.handshakeBuffer);
        }

        @Override
        public HandshakeC2SPayload decode(ByteBuf buf) {
            return new HandshakeC2SPayload(HandshakeBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<HandshakeC2SPayload> TYPE = PacketType.create(HANDSHAKE_PAYLOAD_ID, HandshakeC2SPayload::new);

    public HandshakeC2SPayload(PacketByteBuf buf) {
        this(HandshakeBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        HandshakeBufferCodec.encode(buf, handshakeBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}
