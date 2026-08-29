package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public final class MessageChunkCodec {
    private MessageChunkCodec() {
    }

    public static void encode(ByteBuf buf, MessageChunkBuffer buffer) {
        buf.writeLong(buffer.transferId().getMostSignificantBits());
        buf.writeLong(buffer.transferId().getLeastSignificantBits());
        buf.writeInt(buffer.messageTypeId());
        buf.writeInt(buffer.sequence());
        buf.writeInt(buffer.chunkCount());
        buf.writeBoolean(buffer.compressed());
        buf.writeInt(buffer.uncompressedSize());
        buf.writeInt(buffer.checksum());
        int dataLength = buffer.dataLength();
        if (dataLength > ChunkedMessageManager.MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Message chunk exceeds the payload limit");
        }
        buf.writeShort(dataLength);
        buffer.writeData(buf);
    }

    public static MessageChunkBuffer decode(ByteBuf buf) {
        UUID transferId = new UUID(buf.readLong(), buf.readLong());
        int messageTypeId = buf.readInt();
        int sequence = buf.readInt();
        int chunkCount = buf.readInt();
        boolean compressed = buf.readBoolean();
        int uncompressedSize = buf.readInt();
        int checksum = buf.readInt();
        int dataLength = buf.readUnsignedShort();
        if (dataLength > ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
                || dataLength > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid message chunk length: " + dataLength);
        }
        byte[] data = new byte[dataLength];
        buf.readBytes(data);
        MessageChunkBuffer result = MessageChunkBuffer.chunk(
                transferId,
                messageTypeId,
                sequence,
                chunkCount,
                compressed,
                uncompressedSize,
                checksum,
                data
        );
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Message-chunk frame has trailing bytes");
        }
        return result;
    }
}
