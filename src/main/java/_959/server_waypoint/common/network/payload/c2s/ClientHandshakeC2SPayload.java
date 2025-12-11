package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.CLIENT_HANDSHAKE;

public record ClientHandshakeC2SPayload(ClientHandshakeBuffer clientHandshakeBuffer) implements ModPayload {
    public static final Identifier CLIENT_HANDSHAKE_PAYLOAD = Identifier.of(ModInfo.MOD_ID, CLIENT_HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<ClientHandshakeC2SPayload> ID = new CustomPayload.Id<>(CLIENT_HANDSHAKE_PAYLOAD);
    public static final PacketCodec<ByteBuf, ClientHandshakeC2SPayload> PACKET_CODEC = new PacketCodec<>() {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<ClientHandshakeC2SPayload> TYPE = PacketType.create(CLIENT_HANDSHAKE_PAYLOAD, ClientHandshakeC2SPayload::new);

    public ClientHandshakeC2SPayload(PacketByteBuf buf) {
        this(ClientHandshakeCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        ClientHandshakeCodec.encode(buf);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}
