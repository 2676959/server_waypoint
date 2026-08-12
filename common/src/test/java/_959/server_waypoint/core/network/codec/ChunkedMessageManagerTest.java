package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedMessageManagerTest {
    @Test
    void compressionAndOutOfOrderChunksReassemble() {
        WaypointData source = messageWithDescription("compressible".repeat(20_000));
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, true, 0);
        assertTrue(packets.get(0).compressed());
        List<MessageChunkBuffer> shuffled = new ArrayList<>(packets);
        Collections.reverse(shuffled);

        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<ChunkedMessage> delivered = new ArrayList<>();
        for (MessageChunkBuffer packet : shuffled) {
            delivered.addAll(receiver.receive("server", wire(packet), ignored -> {}, () -> {}));
        }

        assertEquals(List.of(source), delivered);
    }

    @Test
    void modificationAboveTheDirectPacketLimitUsesChunking() {
        WaypointModificationMessage source = modificationWithDescription("x".repeat(40_000));
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, false, 0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<ChunkedMessage> delivered = new ArrayList<>();

        for (MessageChunkBuffer packet : packets) {
            delivered.addAll(receiver.receive("peer", wire(packet), ignored -> {}, () -> {}));
        }

        assertTrue(packets.size() > 1);
        assertEquals(1, delivered.size());
        WaypointModificationMessage decoded =
                (WaypointModificationMessage) delivered.get(0);
        assertEquals(source.dimensionName(), decoded.dimensionName());
        assertEquals(source.type(), decoded.type());
        assertEquals(source.syncId(), decoded.syncId());
        assertEquals(source.waypoint().description(), decoded.waypoint().description());
    }

    @Test
    void duplicateAndConflictingChunksAreHandled() {
        WaypointData source = messageWithDescription("x".repeat(30_000));
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, false, 0);
        assertTrue(packets.size() > 1);
        MessageChunkBuffer first = packets.get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertTrue(receiver.receive("peer", first, ignored -> {}, () -> {}).isEmpty());
        assertTrue(receiver.receive("peer", first, ignored -> {}, () -> {}).isEmpty());

        byte[] conflictingData = first.data();
        conflictingData[0] ^= 1;
        MessageChunkBuffer conflicting = copyChunk(first, conflictingData, first.messageTypeId());
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", conflicting, ignored -> {}, () -> {})
        );

        MessageChunkBuffer conflictingType = copyChunk(
                first,
                first.data(),
                _959.server_waypoint.core.network.ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", conflictingType, ignored -> {}, () -> {})
        );

        for (int index = 1; index < packets.size(); index++) {
            receiver.receive("peer", packets.get(index), ignored -> {}, () -> {});
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", conflicting, ignored -> {}, () -> {})
        );
    }

    @Test
    void missingChunkRetryCompletesAndAcknowledges() {
        WaypointData source = messageWithDescription("0123456789".repeat(5_000));
        ChunkedMessageManager<String> sender = new ChunkedMessageManager<>();
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<MessageChunkBuffer> sent = new ArrayList<>();
        sender.send("peer", source, false, sent::add);
        assertTrue(sent.size() > 2);

        List<MessageChunkBuffer> responses = new ArrayList<>();
        int missing = 1;
        for (int index = 0; index < sent.size(); index++) {
            if (index != missing) {
                assertTrue(receiver.receive("peer", wire(sent.get(index)), responses::add, () -> {}).isEmpty());
            }
        }
        MessageChunkBuffer retry = responses.stream()
                .filter(frame -> frame.operation() == MessageChunkBuffer.Operation.RETRY)
                .findFirst()
                .orElseThrow();
        assertArrayEquals(new int[]{missing}, retry.missingSequences());

        List<MessageChunkBuffer> retransmitted = new ArrayList<>();
        sender.receive("peer", wire(retry), retransmitted::add, () -> {});
        List<ChunkedMessage> delivered = receiver.receive(
                "peer",
                wire(retransmitted.get(0)),
                responses::add,
                () -> {}
        );
        assertEquals(List.of(source), delivered);
        assertTrue(responses.stream().anyMatch(
                frame -> frame.operation() == MessageChunkBuffer.Operation.ACKNOWLEDGEMENT
        ));
    }

    @Test
    void senderProbeRecoversEntireFirstChunkLoss() {
        WaypointData source = messageWithDescription("probe");
        ChunkedMessageManager<String> sender = new ChunkedMessageManager<>();
        List<MessageChunkBuffer> sent = new ArrayList<>();
        sender.send("peer", source, false, sent::add);
        MessageChunkBuffer originalFirst = sent.get(0);
        sent.clear();

        sender.tick(System.nanoTime() + TimeUnit.SECONDS.toNanos(3));

        assertEquals(1, sent.size());
        assertEquals(originalFirst.transferId(), sent.get(0).transferId());
        assertEquals(0, sent.get(0).sequence());
    }

    @Test
    void completedMessagesDeliverInLogicalSequenceOrder() {
        WaypointData first = messageWithDescription("first");
        WaypointData second = messageWithDescription("second");
        MessageChunkBuffer firstFrame =
                ChunkedMessageManager.createTransfer(first, false, 0).get(0);
        MessageChunkBuffer secondFrame =
                ChunkedMessageManager.createTransfer(second, false, 1).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertTrue(receiver.receive("peer", secondFrame, ignored -> {}, () -> {}).isEmpty());
        assertEquals(
                List.of(first, second),
                receiver.receive("peer", firstFrame, ignored -> {}, () -> {})
        );
    }

    @Test
    void timeoutCancelsBlockedQueueAndDisconnectClearsSequences() {
        WaypointData second = messageWithDescription("second");
        WaypointData resync = messageWithDescription("resync");
        MessageChunkBuffer secondFrame =
                ChunkedMessageManager.createTransfer(second, false, 1).get(0);
        MessageChunkBuffer resyncFrame =
                ChunkedMessageManager.createTransfer(resync, false, 2).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        AtomicInteger failures = new AtomicInteger();

        assertTrue(receiver.receive("peer", secondFrame, ignored -> {}, failures::incrementAndGet).isEmpty());
        receiver.tick(System.nanoTime() + TimeUnit.SECONDS.toNanos(31));
        assertEquals(1, failures.get());
        assertEquals(
                List.of(resync),
                receiver.receive("peer", resyncFrame, ignored -> {}, failures::incrementAndGet)
        );

        ChunkedMessageManager<String> sender = new ChunkedMessageManager<>();
        List<MessageChunkBuffer> sent = new ArrayList<>();
        sender.send("peer", second, false, sent::add);
        assertEquals(0, sent.get(0).logicalSequence());
        sender.clear("peer");
        sent.clear();
        sender.send("peer", resync, false, sent::add);
        assertEquals(0, sent.get(0).logicalSequence());
    }

    @Test
    void completedOrderingQueueHasAnObjectRetentionLimit() {
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        AtomicInteger failures = new AtomicInteger();
        for (long sequence = 1; sequence <= 65; sequence++) {
            MessageChunkBuffer frame = ChunkedMessageManager.createTransfer(
                    messageWithDescription("queued-" + sequence),
                    false,
                    sequence
            ).get(0);
            assertTrue(receiver.receive(
                    "peer",
                    frame,
                    ignored -> {},
                    failures::incrementAndGet
            ).isEmpty());
        }
        assertEquals(1, failures.get());

        WaypointData resync = messageWithDescription("resync");
        MessageChunkBuffer resyncFrame =
                ChunkedMessageManager.createTransfer(resync, false, 66).get(0);
        assertEquals(
                List.of(resync),
                receiver.receive("peer", resyncFrame, ignored -> {}, failures::incrementAndGet)
        );
    }

    @Test
    void trailingLogicalBytesAreRejected() {
        MessageChunkBuffer original =
                ChunkedMessageManager.createTransfer(messageWithDescription("trailing"), false, 0).get(0);
        byte[] withTrailing = java.util.Arrays.copyOf(original.data(), original.data().length + 1);
        withTrailing[withTrailing.length - 1] = 42;
        CRC32 crc32 = new CRC32();
        crc32.update(withTrailing);
        MessageChunkBuffer malformed = MessageChunkBuffer.chunk(
                original.transferId(),
                original.logicalSequence(),
                original.messageTypeId(),
                original.sequence(),
                original.chunkCount(),
                false,
                withTrailing.length,
                (int) crc32.getValue(),
                withTrailing
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChunkedMessageManager<String>().receive(
                        "peer",
                        malformed,
                        ignored -> {},
                        () -> {}
                )
        );
    }

    @Test
    void exactly64MiBEncodesButOversizeEmitsNoChunks() {
        WaypointModificationMessage empty = modificationWithDescription("");
        int baseSize = ChunkedMessageManager.createTransfer(empty, false, 0).stream()
                .mapToInt(frame -> frame.data().length)
                .sum();
        WaypointModificationMessage exact = modificationWithDescription(
                "a".repeat(ChunkedMessageManager.MAX_MESSAGE_BYTES - baseSize)
        );
        List<MessageChunkBuffer> exactFrames =
                ChunkedMessageManager.createTransfer(exact, false, 0);
        assertEquals(
                ChunkedMessageManager.MAX_MESSAGE_BYTES,
                exactFrames.stream().mapToInt(frame -> frame.data().length).sum()
        );

        WaypointModificationMessage oversized = modificationWithDescription(
                "a".repeat(ChunkedMessageManager.MAX_MESSAGE_BYTES - baseSize + 1)
        );
        List<MessageChunkBuffer> emitted = new ArrayList<>();
        assertThrows(
                MessageEncodingException.class,
                () -> new ChunkedMessageManager<String>().send(
                        "peer",
                        oversized,
                        false,
                        emitted::add
                )
        );
        assertTrue(emitted.isEmpty());
    }

    @Test
    void physicalFrameFitsConservativeSinglePacketBoundary() {
        MessageChunkBuffer frame = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(30_000)),
                false,
                0
        ).get(0);

        byte[] encoded = SinglePacketMessageEncoder.encode(frame);

        assertTrue(encoded.length <= SinglePacketMessageEncoder.MAX_ENCODED_BYTES);
        assertFalse(frame.data().length == 0);
    }

    private static MessageChunkBuffer copyChunk(
            MessageChunkBuffer source,
            byte[] data,
            int messageTypeId
    ) {
        return MessageChunkBuffer.chunk(
                source.transferId(),
                source.logicalSequence(),
                messageTypeId,
                source.sequence(),
                source.chunkCount(),
                source.compressed(),
                source.uncompressedSize(),
                source.checksum(),
                data
        );
    }

    private static MessageChunkBuffer wire(MessageChunkBuffer frame) {
        byte[] bytes = SinglePacketMessageEncoder.encode(frame);
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            MessageChunkBuffer decoded = MessageChunkCodec.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static WaypointData messageWithDescription(String description) {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "",
                "",
                "",
                new WaypointPos(0, 64, 0),
                0,
                0,
                false,
                List.of(),
                description
        );
        return WaypointData.world(List.of(new DimensionWaypointData(
                "",
                List.of(new WaypointList("", "", 1, List.of(waypoint)))
        )));
    }

    private static WaypointModificationMessage modificationWithDescription(String description) {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "waypoint",
                "W",
                new WaypointPos(0, 64, 0),
                0,
                0,
                false,
                List.of(),
                description
        );
        return new WaypointModificationMessage(
                "minecraft:overworld",
                "list",
                "list",
                "waypoint",
                waypoint,
                WaypointModificationType.ADD,
                2
        );
    }
}
