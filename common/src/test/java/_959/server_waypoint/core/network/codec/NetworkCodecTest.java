package _959.server_waypoint.core.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkCodecTest {
    @Test
    void utfStringRoundTripsStringsAtSignedByteBoundary() {
        String value = "a".repeat(128);
        ByteBuf buf = Unpooled.buffer();

        UtfStringCodec.encode(buf, value);

        assertEquals(value, UtfStringCodec.decode(buf));
    }

    @Test
    void utfStringTruncatesOverlongStringsWithoutThrowing() {
        String value = "a".repeat(256);
        ByteBuf buf = Unpooled.buffer();

        assertDoesNotThrow(() -> UtfStringCodec.encode(buf, value));

        assertEquals("a".repeat(255), UtfStringCodec.decode(buf));
    }

    @Test
    void listDecodeIgnoresOversizedCountsWithoutThrowing() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(10_001);

        List<String> decoded = assertDoesNotThrow(() -> ListCodec.decode(buf, ignored -> "item"));

        assertEquals(List.of(), decoded);
    }
}
