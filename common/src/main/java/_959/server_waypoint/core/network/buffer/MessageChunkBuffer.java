package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.MESSAGE_CHUNK_CHANNEL;

/** One immutable data frame on the shared physical chunk channel. */
public record MessageChunkBuffer(
        UUID transferId,
        int messageTypeId,
        int sequence,
        int chunkCount,
        boolean compressed,
        int uncompressedSize,
        int checksum,
        byte[] data
) implements SinglePacketMessage {
    public MessageChunkBuffer {
        Objects.requireNonNull(transferId, "transferId");
        data = Arrays.copyOf(data, data.length);
    }

    public static MessageChunkBuffer chunk(
            UUID transferId,
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
                messageTypeId,
                sequence,
                chunkCount,
                compressed,
                uncompressedSize,
                checksum,
                data
        );
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    public int dataLength() {
        return this.data.length;
    }

    public void writeData(ByteBuf byteBuf) {
        byteBuf.writeBytes(this.data);
    }

    @Override
    public MessageChannelID getChannelId() {
        return MESSAGE_CHUNK_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        MessageChunkCodec.encode(byteBuf, this);
    }
}
