package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
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


import static _959.server_waypoint.core.network.PayloadID.CLIENT_HANDSHAKE;

public record ClientHandshakeC2SPayload(ClientHandshakeBuffer clientHandshakeBuffer) implements ModPayload {
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ CLIENT_HANDSHAKE_PAYLOAD = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, CLIENT_HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<ClientHandshakeC2SPayload> ID = new CustomPacketPayload.Type<>(CLIENT_HANDSHAKE_PAYLOAD);
    public static final StreamCodec<ByteBuf, ClientHandshakeC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, ClientHandshakeC2SPayload value) {
            ClientHandshakeCodec.encode(buf);
        }

        @Override
        public ClientHandshakeC2SPayload decode(ByteBuf buf) {
            return new ClientHandshakeC2SPayload(ClientHandshakeCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<ClientHandshakeC2SPayload> ID = PacketType.create(CLIENT_HANDSHAKE_PAYLOAD, ClientHandshakeC2SPayload::new);

    public ClientHandshakeC2SPayload(FriendlyByteBuf buf) {
        this(ClientHandshakeCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        ClientHandshakeCodec.encode(buf);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
