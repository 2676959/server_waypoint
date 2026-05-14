package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
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

import static _959.server_waypoint.core.network.PayloadID.SERVER_HANDSHAKE;

public record ServerHandshakeS2CPayload(ServerHandshakeBuffer serverHandshakeBuffer) implements ModPayload {
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ SERVER_HANDSHAKE_PAYLOAD = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, SERVER_HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<ServerHandshakeS2CPayload> ID = new CustomPacketPayload.Type<>(SERVER_HANDSHAKE_PAYLOAD);
    public static final StreamCodec<ByteBuf, ServerHandshakeS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, ServerHandshakeS2CPayload value) {
            ServerHandshakeCodec.encode(buf, value.serverHandshakeBuffer);
        }

        @Override
        public ServerHandshakeS2CPayload decode(ByteBuf buf) {
            return new ServerHandshakeS2CPayload(ServerHandshakeCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<ServerHandshakeS2CPayload> ID = PacketType.create(SERVER_HANDSHAKE_PAYLOAD, ServerHandshakeS2CPayload::new);

    public ServerHandshakeS2CPayload(FriendlyByteBuf buf) {
        this(ServerHandshakeCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        ServerHandshakeCodec.encode(buf, serverHandshakeBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
