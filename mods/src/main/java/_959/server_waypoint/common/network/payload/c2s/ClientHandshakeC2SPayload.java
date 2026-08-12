//~ resource_location_import
package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?} else if neoforge || forge {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}


import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;
import static _959.server_waypoint.core.network.PayloadID.CLIENT_HANDSHAKE;

public record ClientHandshakeC2SPayload(
        ClientHandshakeBuffer clientHandshakeBuffer,
        byte[] encodedMessage
) implements ModPayload {
    public ClientHandshakeC2SPayload {
        encodedMessage = java.util.Arrays.copyOf(encodedMessage, encodedMessage.length);
    }

    public ClientHandshakeC2SPayload(ClientHandshakeBuffer clientHandshakeBuffer) {
        this(clientHandshakeBuffer, SinglePacketMessageEncoder.encode(clientHandshakeBuffer));
    }

    @Override
    public byte[] encodedMessage() {
        return java.util.Arrays.copyOf(this.encodedMessage, this.encodedMessage.length);
    }

    private static ClientHandshakeBuffer decodeMessage(ByteBuf buf) {
        ClientHandshakeBuffer result = ClientHandshakeCodec.decode(buf);
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Client-handshake payload has trailing bytes");
        }
        return result;
    }
    public static final
    //$ resource_location_type_swap
    Identifier
    CLIENT_HANDSHAKE_PAYLOAD = modId(CLIENT_HANDSHAKE);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<ClientHandshakeC2SPayload> ID = new CustomPacketPayload.Type<>(CLIENT_HANDSHAKE_PAYLOAD);
    public static final StreamCodec<ByteBuf, ClientHandshakeC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, ClientHandshakeC2SPayload value) {
            buf.writeBytes(value.encodedMessage());
        }

        @Override
        public ClientHandshakeC2SPayload decode(ByteBuf buf) {
            return new ClientHandshakeC2SPayload(decodeMessage(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<ClientHandshakeC2SPayload> ID = PacketType.create(CLIENT_HANDSHAKE_PAYLOAD, ClientHandshakeC2SPayload::new);

    public ClientHandshakeC2SPayload(FriendlyByteBuf buf) {
        this(decodeMessage(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public ClientHandshakeC2SPayload(FriendlyByteBuf buf) {
        this(decodeMessage(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return CLIENT_HANDSHAKE_PAYLOAD;
    }
    ^///?}
*///?}
}
