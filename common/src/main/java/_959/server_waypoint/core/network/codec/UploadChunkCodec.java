package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public final class UploadChunkCodec {
    private UploadChunkCodec() {
    }

    public static void encode(ByteBuf buf, UploadChunkBuffer buffer) {
        buf.writeLong(buffer.requestId().getMostSignificantBits());
        buf.writeLong(buffer.requestId().getLeastSignificantBits());
        MessageChunkCodec.encode(buf, buffer.messageChunk());
    }

    public static UploadChunkBuffer decode(ByteBuf buf) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        return new UploadChunkBuffer(requestId, MessageChunkCodec.decode(buf));
    }
}
