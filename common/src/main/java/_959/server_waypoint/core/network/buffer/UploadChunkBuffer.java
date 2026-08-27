package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.codec.UploadChunkCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.UPLOAD_CHUNK_CHANNEL;

/** One lease-bound frame on the dedicated client-to-server upload channel. */
public record UploadChunkBuffer(
        UUID requestId,
        MessageChunkBuffer messageChunk
) implements SinglePacketMessage {
    public UploadChunkBuffer {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(messageChunk, "messageChunk");
        if (messageChunk.operation() != MessageChunkBuffer.Operation.CHUNK) {
            throw new IllegalArgumentException("Upload transport only accepts data chunks");
        }
    }

    @Override
    public MessageChannelID getChannelId() {
        return UPLOAD_CHUNK_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        UploadChunkCodec.encode(byteBuf, this);
    }
}
