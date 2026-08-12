package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.MESSAGE_CHUNK_CHANNEL;

/** One data or recovery-control frame on the shared physical chunk channel. */
public record MessageChunkBuffer(
        UUID transferId,
        Operation operation,
        long logicalSequence,
        int messageTypeId,
        int sequence,
        int chunkCount,
        boolean compressed,
        int uncompressedSize,
        int checksum,
        byte[] data,
        int[] missingSequences
) implements SinglePacketMessage {
    public MessageChunkBuffer {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(operation, "operation");
        data = Arrays.copyOf(data, data.length);
        missingSequences = Arrays.copyOf(missingSequences, missingSequences.length);
    }

    public static MessageChunkBuffer chunk(
            UUID transferId,
            long logicalSequence,
            int messageTypeId,
            int sequence,
            int chunkCount,
            boolean compressed,
            int uncompressedSize,
            int checksum,
            byte[] data
    ) {
        return new MessageChunkBuffer(
                transferId,
                Operation.CHUNK,
                logicalSequence,
                messageTypeId,
                sequence,
                chunkCount,
                compressed,
                uncompressedSize,
                checksum,
                data,
                new int[0]
        );
    }

    public static MessageChunkBuffer acknowledgement(UUID transferId) {
        return control(transferId, Operation.ACKNOWLEDGEMENT, new int[0]);
    }

    public static MessageChunkBuffer retry(UUID transferId, int[] missingSequences) {
        return control(transferId, Operation.RETRY, missingSequences);
    }

    private static MessageChunkBuffer control(
            UUID transferId,
            Operation operation,
            int[] missingSequences
    ) {
        return new MessageChunkBuffer(
                transferId,
                operation,
                -1,
                -1,
                0,
                0,
                false,
                0,
                0,
                new byte[0],
                missingSequences
        );
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    public int dataLength() {
        return this.data.length;
    }

    @Override
    public int[] missingSequences() {
        return Arrays.copyOf(this.missingSequences, this.missingSequences.length);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MESSAGE_CHUNK_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        MessageChunkCodec.encode(byteBuf, this);
    }

    public enum Operation {
        CHUNK,
        ACKNOWLEDGEMENT,
        RETRY
    }
}
