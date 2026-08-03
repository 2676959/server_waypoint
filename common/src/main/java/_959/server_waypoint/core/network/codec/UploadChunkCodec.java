package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UploadChunkCodec {
    private UploadChunkCodec() {
    }

    public static void encode(ByteBuf buf, UploadChunkBuffer chunk) {
        buf.writeLong(chunk.requestId().getMostSignificantBits());
        buf.writeLong(chunk.requestId().getLeastSignificantBits());
        buf.writeInt(chunk.sequence());
        buf.writeBoolean(chunk.finalChunk());
        buf.writeByte(chunk.status().ordinal());
        if (chunk.waypointLists().size() > UploadCodecSupport.MAX_LISTS_PER_CHUNK) {
            throw new IllegalArgumentException("Too many waypoint lists in upload chunk");
        }
        buf.writeByte(chunk.waypointLists().size());
        for (UploadedWaypointListChunk waypointList : chunk.waypointLists()) {
            UploadCodecSupport.encodeListChunk(buf, waypointList);
        }
    }

    public static UploadChunkBuffer decode(ByteBuf buf) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        int sequence = buf.readInt();
        boolean finalChunk = buf.readBoolean();
        int statusId = buf.readUnsignedByte();
        UploadStatus[] statuses = UploadStatus.values();
        if (statusId >= statuses.length) {
            throw new IllegalArgumentException("Invalid upload status: " + statusId);
        }
        int listCount = buf.readUnsignedByte();
        if (listCount > UploadCodecSupport.MAX_LISTS_PER_CHUNK) {
            throw new IllegalArgumentException("Too many waypoint lists in upload chunk: " + listCount);
        }
        List<UploadedWaypointListChunk> waypointLists = new ArrayList<>(listCount);
        for (int i = 0; i < listCount; i++) {
            waypointLists.add(UploadCodecSupport.decodeListChunk(buf));
        }
        return new UploadChunkBuffer(requestId, sequence, finalChunk, statuses[statusId], waypointLists);
    }
}
