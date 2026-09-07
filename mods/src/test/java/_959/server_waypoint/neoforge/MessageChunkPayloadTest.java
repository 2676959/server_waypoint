//? if neoforge && >=1.20.4 {
/*package _959.server_waypoint.neoforge;

import _959.server_waypoint.common.network.MessagePayloadMapping;
import _959.server_waypoint.common.network.payload.c2s.MessageChunkC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.MessageChunkS2CPayload;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageChunkPayloadTest {
    private static MessageChunkBuffer message() {
        return MessageChunkBuffer.chunk(new UUID(1, 2), 1, 0, 1, false, 3, 42, new byte[]{3, 2, 1});
    }

    @Test
    void serverMappingUsesTheBidirectionalRuntimeTypeAndOriginalWireBytes() {
        MessageChunkBuffer message = message();
        byte[] bytes = SinglePacketMessageEncoder.encode(message);
        MessageChunkPayload payload = assertInstanceOf(MessageChunkPayload.class, MessagePayloadMapping.getPayload(message, bytes));
        assertArrayEquals(new MessageChunkS2CPayload(message).encodedMessage(), payload.encodedMessage());
        assertArrayEquals(new MessageChunkC2SPayload(message).encodedMessage(), payload.encodedMessage());
        assertEquals(MessageChunkS2CPayload.MESSAGE_CHUNK_PAYLOAD_ID, MessageChunkPayload.MESSAGE_CHUNK_PAYLOAD_ID);
        bytes[0] ^= 1;
        assertArrayEquals(SinglePacketMessageEncoder.encode(message), payload.encodedMessage());
    }

    @Test
    void registeredCodecRoundTripsAndRejectsTrailingBytes() {
        MessageChunkPayload original = new MessageChunkPayload(message());
        ByteBuf wire = Unpooled.buffer();
        try {
            //? if >=1.20.5 {
            MessageChunkPayload.PACKET_CODEC.encode(wire, original);
            MessageChunkPayload decoded = MessageChunkPayload.PACKET_CODEC.decode(wire);
            //?} else {
            /^original.write(new FriendlyByteBuf(wire));
            MessageChunkPayload decoded = new MessageChunkPayload(new FriendlyByteBuf(wire));
            ^///?}
            assertArrayEquals(original.encodedMessage(), decoded.encodedMessage());
            assertFalse(wire.isReadable());
            wire.clear().writeBytes(original.encodedMessage()).writeByte(0);
            //? if >=1.20.5 {
            assertThrows(IllegalArgumentException.class, () -> MessageChunkPayload.PACKET_CODEC.decode(wire));
            //?} else {
            /^assertThrows(IllegalArgumentException.class, () -> new MessageChunkPayload(new FriendlyByteBuf(wire)));
            ^///?}
        } finally {
            wire.release();
        }
    }
}
*///?}
