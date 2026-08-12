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
        buf.writeByte(buffer.operation().ordinal());
        switch (buffer.operation()) {
            case CHUNK -> encodeChunk(buf, buffer);
            case ACKNOWLEDGEMENT -> {
            }
            case RETRY -> encodeRetry(buf, buffer);
        }
    }

    public static MessageChunkBuffer decode(ByteBuf buf) {
        UUID transferId = new UUID(buf.readLong(), buf.readLong());
        int operationId = buf.readUnsignedByte();
        MessageChunkBuffer.Operation[] operations = MessageChunkBuffer.Operation.values();
        if (operationId >= operations.length) {
            throw new IllegalArgumentException("Invalid message-chunk operation: " + operationId);
        }
        MessageChunkBuffer result = switch (operations[operationId]) {
            case CHUNK -> decodeChunk(buf, transferId);
            case ACKNOWLEDGEMENT -> MessageChunkBuffer.acknowledgement(transferId);
            case RETRY -> decodeRetry(buf, transferId);
        };
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Message-chunk frame has trailing bytes");
        }
        return result;
    }

    private static void encodeChunk(ByteBuf buf, MessageChunkBuffer buffer) {
        byte[] data = buffer.data();
        if (data.length > ChunkedMessageManager.MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Message chunk exceeds the payload limit");
        }
        buf.writeLong(buffer.logicalSequence());
        buf.writeInt(buffer.messageTypeId());
        buf.writeInt(buffer.sequence());
        buf.writeInt(buffer.chunkCount());
        buf.writeBoolean(buffer.compressed());
        buf.writeInt(buffer.uncompressedSize());
        buf.writeInt(buffer.checksum());
        buf.writeShort(data.length);
        buf.writeBytes(data);
    }

    private static MessageChunkBuffer decodeChunk(ByteBuf buf, UUID transferId) {
        long logicalSequence = buf.readLong();
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
        return MessageChunkBuffer.chunk(
                transferId,
                logicalSequence,
                messageTypeId,
                sequence,
                chunkCount,
                compressed,
                uncompressedSize,
                checksum,
                data
        );
    }

    private static void encodeRetry(ByteBuf buf, MessageChunkBuffer buffer) {
        int[] missingSequences = buffer.missingSequences();
        if (missingSequences.length == 0
                || missingSequences.length > ChunkedMessageManager.MAX_CHUNKS_PER_TRANSFER) {
            throw new IllegalArgumentException("Invalid message-chunk retry sequence count");
        }
        buf.writeShort(missingSequences.length);
        for (int missingSequence : missingSequences) {
            buf.writeInt(missingSequence);
        }
    }

    private static MessageChunkBuffer decodeRetry(ByteBuf buf, UUID transferId) {
        int missingCount = buf.readUnsignedShort();
        if (missingCount == 0
                || missingCount > ChunkedMessageManager.MAX_CHUNKS_PER_TRANSFER) {
            throw new IllegalArgumentException("Invalid message-chunk retry sequence count: " + missingCount);
        }
        int[] missingSequences = new int[missingCount];
        for (int i = 0; i < missingCount; i++) {
            missingSequences[i] = buf.readInt();
        }
        return MessageChunkBuffer.retry(transferId, missingSequences);
    }
}
