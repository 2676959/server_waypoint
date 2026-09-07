//~ resource_location_import
//? if neoforge && >=1.20.4 {
/*package _959.server_waypoint.neoforge;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/^import net.minecraft.network.FriendlyByteBuf;
^///?}

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;
import static _959.server_waypoint.core.network.PayloadID.MESSAGE_CHUNK;

/^* One runtime type for NeoForge's bidirectional message_chunk registration. ^/
public record MessageChunkPayload(MessageChunkBuffer messageChunk, byte[] encodedMessage) implements ModPayload {
    public MessageChunkPayload {
        encodedMessage = java.util.Arrays.copyOf(encodedMessage, encodedMessage.length);
    }

    public MessageChunkPayload(MessageChunkBuffer messageChunk) {
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
//? if >=1.20.5 {
    public static final CustomPacketPayload.Type<MessageChunkPayload> ID = new CustomPacketPayload.Type<>(MESSAGE_CHUNK_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, MessageChunkPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, MessageChunkPayload payload) {
            buf.writeBytes(payload.encodedMessage);
        }

        @Override
        public MessageChunkPayload decode(ByteBuf buf) {
            return new MessageChunkPayload(MessageChunkCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else {
    /^public MessageChunkPayload(FriendlyByteBuf buf) {
        this(MessageChunkCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(this.encodedMessage);
    }

    @Override
    public net.minecraft.resources.Identifier id() {
        return MESSAGE_CHUNK_PAYLOAD_ID;
    }
    ^///?}
}
*///?}
