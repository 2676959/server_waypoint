package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.SERVER_HANDSHAKE;

public record ServerHandshakeS2CPayload(ServerHandshakeBuffer serverHandshakeBuffer) implements ModPayload {
    public static final Identifier SERVER_HANDSHAKE_PAYLOAD = Identifier.of(ModInfo.MOD_ID, SERVER_HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<ServerHandshakeS2CPayload> ID = new CustomPayload.Id<>(SERVER_HANDSHAKE_PAYLOAD);
    public static final PacketCodec<ByteBuf, ServerHandshakeS2CPayload> PACKET_CODEC = new PacketCodec<>() {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<ServerHandshakeS2CPayload> ID = PacketType.create(SERVER_HANDSHAKE_PAYLOAD, ServerHandshakeS2CPayload::new);

    public ServerHandshakeS2CPayload(PacketByteBuf buf) {
        this(ServerHandshakeCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        ServerHandshakeCodec.encode(buf, serverHandshakeBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
