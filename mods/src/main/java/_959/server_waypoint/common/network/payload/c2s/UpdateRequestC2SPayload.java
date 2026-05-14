package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ClientUpdateRequestBuffer;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
import io.netty.buffer.ByteBuf;
//? if >= 1.21.11
import net.minecraft.resources.Identifier;
//? if < 1.21.11
/*import net.minecraft.resources.ResourceLocation;*/
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}


import static _959.server_waypoint.core.network.PayloadID.CLIENT_UPDATE_REQUEST;

public record UpdateRequestC2SPayload(ClientUpdateRequestBuffer clientUpdateRequestBuffer) implements ModPayload {
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ CLIENT_UPDATE_REQUEST_PAYLOAD = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, CLIENT_UPDATE_REQUEST);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<UpdateRequestC2SPayload> ID = new CustomPacketPayload.Type<>(CLIENT_UPDATE_REQUEST_PAYLOAD);
    public static final StreamCodec<ByteBuf, UpdateRequestC2SPayload> PACKET_CODEC = new StreamCodec<>() {
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<UpdateRequestC2SPayload> ID = PacketType.create(CLIENT_UPDATE_REQUEST_PAYLOAD, UpdateRequestC2SPayload::new);

    public UpdateRequestC2SPayload(FriendlyByteBuf buf) {
        this(ClientUpdateRequestBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        ClientUpdateRequestBufferCodec.encode(buf, clientUpdateRequestBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
