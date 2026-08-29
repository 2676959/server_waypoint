package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedMessageManagerTest {
    @Test
    void outgoingSaturationReturnsTypedBackpressure() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );
        ChunkedMessage message = messageWithDescription("x".repeat(400_000));

        for (int i = 0; i < ChunkedMessageManager.MAX_ACTIVE_TRANSFERS_PER_PEER; i++) {
            assertEquals(
                    ChunkedMessageSendResult.QUEUED,
                    manager.send("peer", message, false, ignored -> {
                    })
            );
        }

        assertEquals(
                ChunkedMessageSendResult.PEER_BUSY,
                manager.send("peer", message, false, ignored -> {
                })
        );
    }

    @Test
    void compressionAndOutOfOrderChunksReassemble() {
        WaypointData source = messageWithDescription("compressible".repeat(20_000));
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, true);
        assertTrue(packets.get(0).compressed());
        List<MessageChunkBuffer> shuffled = new ArrayList<>(packets);
        Collections.reverse(shuffled);

        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<ChunkedMessage> delivered = new ArrayList<>();
        for (MessageChunkBuffer packet : shuffled) {
            delivered.addAll(receiver.receive("server", wire(packet), () -> {
            }));
        }

        assertEquals(List.of(source), delivered);
    }

    @Test
    void modificationAboveTheDirectPacketLimitUsesChunking() {
        WaypointModificationMessage source = modificationWithDescription("x".repeat(40_000));
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, false);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<ChunkedMessage> delivered = new ArrayList<>();

        for (MessageChunkBuffer packet : packets) {
            delivered.addAll(receiver.receive("peer", wire(packet), () -> {
            }));
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
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(source, false);
        assertTrue(packets.size() > 1);
        MessageChunkBuffer first = packets.get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertTrue(receiver.receive("peer", first, () -> {
        }).isEmpty());
        assertTrue(receiver.receive("peer", first, () -> {
        }).isEmpty());

        byte[] conflictingData = first.data();
        conflictingData[0] ^= 1;
        MessageChunkBuffer conflicting = copyChunk(first, conflictingData, first.messageTypeId());
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", conflicting, () -> {
                })
        );

        MessageChunkBuffer conflictingType = copyChunk(
                first,
                first.data(),
                _959.server_waypoint.core.network.ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
        );
        ChunkedMessageManager<String> metadataReceiver = new ChunkedMessageManager<>();
        assertTrue(metadataReceiver.receive("peer", first, () -> {
        }).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> metadataReceiver.receive("peer", conflictingType, () -> {
                })
        );
    }

    @Test
    void deliveryIsPacedByFrameAndByteBudgets() {
        WaypointData source = messageWithDescription("x".repeat(200_000));
        ChunkedMessageManager.PreparedMessage prepared =
                ChunkedMessageManager.prepare(source, false);
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                2,
                2 * ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );
        List<List<MessageChunkBuffer>> batches = new ArrayList<>();

        assertEquals(
                ChunkedMessageSendResult.QUEUED,
                manager.send("peer", prepared, batches::add)
        );
        while (batches.stream().mapToInt(List::size).sum() < prepared.frames().size()) {
            manager.tick();
        }

        assertTrue(batches.size() > 1);
        assertTrue(batches.stream().allMatch(batch -> batch.size() <= 2));
        assertTrue(batches.stream().allMatch(batch -> batch.stream()
                .mapToInt(MessageChunkBuffer::dataLength)
                .sum() <= 2 * ChunkedMessageManager.MAX_CHUNK_DATA_SIZE));
    }

    @Test
    void onePreparedBodyIsAccountedOnceAcrossPeers() {
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                messageWithDescription("x".repeat(100_000)),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                prepared.retainedBytes(),
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );

        assertEquals(
                ChunkedMessageSendResult.QUEUED,
                manager.send("first", prepared, ignored -> {
                })
        );
        assertEquals(
                ChunkedMessageSendResult.QUEUED,
                manager.send("second", prepared, ignored -> {
                })
        );
        assertEquals(prepared.retainedBytes(), manager.globallyRetainedBytes());

        manager.clear("first");
        assertEquals(prepared.retainedBytes(), manager.globallyRetainedBytes());
        manager.clear("second");
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void globalBudgetRejectsAnotherUniqueBody() {
        ChunkedMessageManager.PreparedMessage first = ChunkedMessageManager.prepare(
                messageWithDescription("a".repeat(100_000)),
                false
        );
        ChunkedMessageManager.PreparedMessage second = ChunkedMessageManager.prepare(
                messageWithDescription("b".repeat(100_000)),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                first.retainedBytes(),
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );

        assertEquals(
                ChunkedMessageSendResult.QUEUED,
                manager.send("first", first, ignored -> {
                })
        );
        assertEquals(
                ChunkedMessageSendResult.PEER_BUSY,
                manager.send("second", second, ignored -> {
                })
        );
    }

    @Test
    void blockedPeerCallbackDoesNotBlockAnotherPeer() throws Exception {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ChunkedMessageSendResult> blocked = executor.submit(() -> manager.send(
                    "blocked",
                    messageWithDescription("blocked"),
                    false,
                    ignored -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }
            ));
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertEquals(
                    ChunkedMessageSendResult.QUEUED,
                    manager.send("unrelated", messageWithDescription("ready"), false, ignored -> {
                    })
            );
            release.countDown();
            assertEquals(ChunkedMessageSendResult.QUEUED, blocked.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void incompleteTransferExpiresAndReleasesItsReservation() {
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(40_000)),
                false
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        AtomicInteger failures = new AtomicInteger();

        assertTrue(receiver.receive(
                "peer",
                packets.get(0),
                failures::incrementAndGet
        ).isEmpty());
        assertTrue(receiver.globallyRetainedBytes() > 0);
        receiver.tick(System.nanoTime() + TimeUnit.SECONDS.toNanos(31));

        assertEquals(1, failures.get());
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void trailingLogicalBytesAreRejected() {
        MessageChunkBuffer original = ChunkedMessageManager.createTransfer(
                messageWithDescription("trailing"),
                false
        ).get(0);
        byte[] withTrailing = java.util.Arrays.copyOf(original.data(), original.data().length + 1);
        withTrailing[withTrailing.length - 1] = 42;
        CRC32 crc32 = new CRC32();
        crc32.update(withTrailing);
        MessageChunkBuffer malformed = MessageChunkBuffer.chunk(
                original.transferId(),
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
                        () -> {
                        }
                )
        );
    }

    @Test
    void declaredSizeCannotUnderstateRetainedData() {
        MessageChunkBuffer original = ChunkedMessageManager.createTransfer(
                messageWithDescription("understated"),
                false
        ).get(0);
        MessageChunkBuffer malformed = MessageChunkBuffer.chunk(
                original.transferId(),
                original.messageTypeId(),
                original.sequence(),
                original.chunkCount(),
                true,
                0,
                original.checksum(),
                original.data()
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", malformed, () -> {
                })
        );
        assertFalse(receiver.hasPending("peer"));
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void receiveByteLimitRejectsTransferBeforeAdmission() {
        MessageChunkBuffer packet = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(256)),
                false
        ).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive(
                        "peer",
                        packet,
                        () -> {
                        },
                        new ChunkedMessageManager.ReceiveLimits(128, 16)
                )
        );
        assertFalse(receiver.hasPending("peer"));
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void receiveObjectLimitAppliesToCanonicalDecoder() {
        ClientUpdateRequestMessage request = new ClientUpdateRequestMessage(List.of(
                new DimensionSyncIdentifier("first", List.of()),
                new DimensionSyncIdentifier("second", List.of())
        ));
        MessageChunkBuffer packet = ChunkedMessageManager.createTransfer(request, false).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive(
                        "peer",
                        packet,
                        () -> {
                        },
                        new ChunkedMessageManager.ReceiveLimits(1_024, 1)
                )
        );
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void exactly64MiBEncodesButOversizeEmitsNoChunks() {
        WaypointModificationMessage empty = modificationWithDescription("");
        int baseSize = ChunkedMessageManager.createTransfer(empty, false).stream()
                .mapToInt(MessageChunkBuffer::dataLength)
                .sum();
        WaypointModificationMessage exact = modificationWithDescription(
                "a".repeat(ChunkedMessageManager.MAX_MESSAGE_BYTES - baseSize)
        );
        List<MessageChunkBuffer> exactFrames =
                ChunkedMessageManager.createTransfer(exact, false);
        assertEquals(
                ChunkedMessageManager.MAX_MESSAGE_BYTES,
                exactFrames.stream().mapToInt(MessageChunkBuffer::dataLength).sum()
        );

        WaypointModificationMessage oversized = modificationWithDescription(
                "a".repeat(ChunkedMessageManager.MAX_MESSAGE_BYTES - baseSize + 1)
        );
        assertThrows(
                MessageEncodingException.class,
                () -> ChunkedMessageManager.prepare(oversized, false)
        );
    }

    @Test
    void physicalFrameFitsConservativeSinglePacketBoundary() {
        MessageChunkBuffer frame = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(30_000)),
                false
        ).get(0);

        byte[] encoded = SinglePacketMessageEncoder.encode(frame);

        assertTrue(encoded.length <= SinglePacketMessageEncoder.MAX_ENCODED_BYTES);
        assertFalse(frame.dataLength() == 0);
    }

    private static MessageChunkBuffer copyChunk(
            MessageChunkBuffer source,
            byte[] data,
            int messageTypeId
    ) {
        return MessageChunkBuffer.chunk(
                source.transferId(),
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
