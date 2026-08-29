package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadChunkCodecTest {
    @Test
    void roundTripsLeaseBeforeChunkData() {
        UUID requestId = UUID.randomUUID();
        MessageChunkBuffer chunk = MessageChunkBuffer.chunk(
                UUID.randomUUID(),
                0,
                0,
                1,
                false,
                3,
                123,
                new byte[]{1, 2, 3}
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            UploadChunkCodec.encode(buffer, new UploadChunkBuffer(requestId, chunk));

            UploadChunkBuffer decoded = UploadChunkCodec.decode(buffer);

            assertEquals(requestId, decoded.requestId());
            assertEquals(chunk.transferId(), decoded.messageChunk().transferId());
            assertEquals(chunk.messageTypeId(), decoded.messageChunk().messageTypeId());
            assertEquals(chunk.sequence(), decoded.messageChunk().sequence());
            assertArrayEquals(chunk.data(), decoded.messageChunk().data());
        } finally {
            buffer.release();
        }
    }
}
