package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.UploadChunkCodec;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.UPLOAD_CHUNK_CHANNEL;

/** One bounded client-to-server portion of an Xaero upload. */
public record UploadChunkBuffer(
        UUID requestId,
        int sequence,
        boolean finalChunk,
        UploadStatus status,
        List<UploadedWaypointListChunk> waypointLists
) implements MessageBuffer {
    public UploadChunkBuffer {
        waypointLists = List.copyOf(waypointLists);
    }

    @Override
    public MessageChannelID getChannelId() {
        return UPLOAD_CHUNK_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        UploadChunkCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return UploadChunkCodec.decode(byteBuf);
    }
}
