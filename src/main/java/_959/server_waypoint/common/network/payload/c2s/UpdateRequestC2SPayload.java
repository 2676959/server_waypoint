package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ClientUpdateRequestBuffer;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.CLIENT_UPDATE_REQUEST;

public record UpdateRequestC2SPayload(ClientUpdateRequestBuffer clientUpdateRequestBuffer) implements ModPayload {
    public static final Identifier CLIENT_UPDATE_REQUEST_PAYLOAD = Identifier.of(ModInfo.MOD_ID, CLIENT_UPDATE_REQUEST);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<UpdateRequestC2SPayload> ID = new CustomPayload.Id<>(CLIENT_UPDATE_REQUEST_PAYLOAD);
    public static final PacketCodec<ByteBuf, UpdateRequestC2SPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, UpdateRequestC2SPayload value) {
            ClientUpdateRequestBufferCodec.encode(buf, value.clientUpdateRequestBuffer);
        }

        @Override
        public UpdateRequestC2SPayload decode(ByteBuf buf) {
            return new UpdateRequestC2SPayload(ClientUpdateRequestBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<UpdateRequestC2SPayload> TYPE = PacketType.create(CLIENT_UPDATE_REQUEST_PAYLOAD, UpdateRequestC2SPayload::new);

    public UpdateRequestC2SPayload(PacketByteBuf buf) {
        this(ClientUpdateRequestBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        ClientUpdateRequestBufferCodec.encode(buf, clientUpdateRequestBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}
