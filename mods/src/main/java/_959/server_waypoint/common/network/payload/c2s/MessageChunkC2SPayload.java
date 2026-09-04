//~ resource_location_import
package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
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
import static _959.server_waypoint.core.network.PayloadID.MESSAGE_CHUNK;

public record MessageChunkC2SPayload(
        MessageChunkBuffer messageChunk,
        byte[] encodedMessage
) implements ModPayload {
    public MessageChunkC2SPayload {
        encodedMessage = java.util.Arrays.copyOf(encodedMessage, encodedMessage.length);
    }

    public MessageChunkC2SPayload(MessageChunkBuffer messageChunk) {
        this(messageChunk, SinglePacketMessageEncoder.encode(messageChunk));
    }

    @Override
    public byte[] encodedMessage() {
        return java.util.Arrays.copyOf(this.encodedMessage, this.encodedMessage.length);
    }
    public static final
    //$ resource_location_type_swap
    Identifier
    MESSAGE_CHUNK_PAYLOAD_ID = modId(MESSAGE_CHUNK);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<MessageChunkC2SPayload> ID =
            new CustomPacketPayload.Type<>(MESSAGE_CHUNK_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, MessageChunkC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, MessageChunkC2SPayload value) {
            buf.writeBytes(value.encodedMessage());
        }

        @Override
        public MessageChunkC2SPayload decode(ByteBuf buf) {
            return new MessageChunkC2SPayload(MessageChunkCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<MessageChunkC2SPayload> ID =
            PacketType.create(MESSAGE_CHUNK_PAYLOAD_ID, MessageChunkC2SPayload::new);

    public MessageChunkC2SPayload(FriendlyByteBuf buf) {
        this(MessageChunkCodec.decode(buf));
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
    /*public MessageChunkC2SPayload(FriendlyByteBuf buf) {
        this(MessageChunkCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return MESSAGE_CHUNK_PAYLOAD_ID;
    }
    ^///?}
*///?}
}
