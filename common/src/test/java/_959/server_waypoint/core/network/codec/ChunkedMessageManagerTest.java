package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedMessageManagerTest {
    @Test
    void trackedDeliveryRetainsAdmissionUntilOwnedBatchCompletes() {
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                messageWithDescription("tracked"),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CompletableFuture<ChunkedMessageSendResult> batch = new CompletableFuture<>();
        AtomicReference<CompletableFuture<ChunkedMessageSendResult>> dispatched =
                new AtomicReference<>();

        ChunkedMessageDelivery delivery = manager.sendTracked(
                "peer",
                prepared,
                ignored -> {
                    dispatched.compareAndSet(null, batch);
                    return batch;
                }
        );

        assertEquals(ChunkedMessageSendResult.QUEUED, delivery.admissionResult());
        assertFalse(delivery.completion().toCompletableFuture().isDone());
        assertEquals(prepared.retainedBytes(), manager.globallyRetainedBytes());
        assertNull(dispatched.get());

        manager.tick();
        assertSame(batch, dispatched.get());
        assertFalse(delivery.completion().toCompletableFuture().isDone());
        batch.complete(ChunkedMessageSendResult.DELIVERED);

        assertEquals(
                ChunkedMessageSendResult.DELIVERED,
                delivery.completion().toCompletableFuture().join()
        );
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void trackedSchedulerFailureCompletesExactlyAndReleasesAdmission() {
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                messageWithDescription("failed"),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CompletableFuture<ChunkedMessageSendResult> batch = new CompletableFuture<>();
        ChunkedMessageDelivery delivery = manager.sendTracked(
                "peer",
                prepared,
                ignored -> batch
        );
        manager.tick();

        batch.complete(ChunkedMessageSendResult.DELIVERY_FAILED);

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                delivery.completion().toCompletableFuture().join()
        );
        assertEquals(0, manager.globallyRetainedBytes());
        assertFalse(manager.hasPending("peer"));
    }

    @Test
    void disconnectCompletesOutstandingTrackedDelivery() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CompletableFuture<ChunkedMessageSendResult> batch = new CompletableFuture<>();
        ChunkedMessageDelivery delivery = manager.sendTracked(
                "peer",
                ChunkedMessageManager.prepare(messageWithDescription("pending"), false),
                ignored -> batch
        );
        manager.tick();

        manager.clear("peer");

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                delivery.completion().toCompletableFuture().join()
        );
        batch.complete(ChunkedMessageSendResult.DELIVERED);
        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                delivery.completion().toCompletableFuture().join()
        );
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void staleBatchFailureCannotClearReplacementPeerState() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CompletableFuture<ChunkedMessageSendResult> oldBatch = new CompletableFuture<>();
        ChunkedMessageDelivery oldDelivery = manager.sendTracked(
                "peer",
                ChunkedMessageManager.prepare(messageWithDescription("old"), false),
                ignored -> oldBatch
        );
        manager.tick();
        manager.clear("peer");
        CompletableFuture<ChunkedMessageSendResult> replacementBatch =
                new CompletableFuture<>();
        ChunkedMessageDelivery replacement = manager.sendTracked(
                "peer",
                ChunkedMessageManager.prepare(messageWithDescription("replacement"), false),
                ignored -> replacementBatch
        );
        manager.tick();

        oldBatch.complete(ChunkedMessageSendResult.DELIVERY_FAILED);

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                oldDelivery.completion().toCompletableFuture().join()
        );
        assertTrue(manager.hasPending("peer"));
        assertFalse(replacement.completion().toCompletableFuture().isDone());

        replacementBatch.complete(ChunkedMessageSendResult.DELIVERED);
        assertEquals(
                ChunkedMessageSendResult.DELIVERED,
                replacement.completion().toCompletableFuture().join()
        );
    }

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
            delivered.addAll(receiver.receive("server", wire(packet)));
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
            delivered.addAll(receiver.receive("peer", wire(packet)));
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

        assertTrue(receiver.receive("peer", first).isEmpty());
        assertTrue(receiver.receive("peer", first).isEmpty());

        byte[] conflictingData = first.data();
        conflictingData[0] ^= 1;
        MessageChunkBuffer conflicting = copyChunk(first, conflictingData, first.messageTypeId());
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.receive("peer", conflicting)
        );

        MessageChunkBuffer conflictingType = copyChunk(
                first,
                first.data(),
                _959.server_waypoint.core.network.ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
        );
        ChunkedMessageManager<String> metadataReceiver = new ChunkedMessageManager<>();
        assertTrue(metadataReceiver.receive("peer", first).isEmpty());
        ChunkedMessageManager.ReceiveException metadataFailure = assertThrows(
                ChunkedMessageManager.ReceiveException.class,
                () -> metadataReceiver.receive("peer", conflictingType)
        );
        assertEquals(first.messageTypeId(), metadataFailure.messageTypeId());
        assertEquals(
                ChunkedMessageManager.FailureReason.MALFORMED,
                metadataFailure.reason()
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
    void admissionEmitsNothingUntilManagerTick() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        List<List<MessageChunkBuffer>> batches = new ArrayList<>();

        ChunkedMessageDelivery delivery = manager.sendTracked(
                "peer",
                ChunkedMessageManager.prepare(messageWithDescription("deferred"), false),
                frames -> {
                    batches.add(frames);
                    return CompletableFuture.completedFuture(
                            ChunkedMessageSendResult.DELIVERED
                    );
                }
        );

        assertEquals(ChunkedMessageSendResult.QUEUED, delivery.admissionResult());
        assertTrue(batches.isEmpty());
        assertFalse(delivery.completion().toCompletableFuture().isDone());
        assertTrue(manager.globallyRetainedBytes() > 0);

        manager.tick();

        assertEquals(1, batches.size());
        assertEquals(
                ChunkedMessageSendResult.DELIVERED,
                delivery.completion().toCompletableFuture().join()
        );
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void managerTickGrantsNeverExceedGlobalLimits() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK,
                ChunkedMessageManager.MAX_BYTES_PER_PEER_TICK,
                10,
                5 * ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );
        Map<String, List<List<MessageChunkBuffer>>> batchesByPeer = new java.util.HashMap<>();
        for (String peer : List.of("a", "b", "c", "d", "e")) {
            manager.sendTracked(
                    peer,
                    ChunkedMessageManager.prepare(messageWithDescription("x".repeat(300_000)), false),
                    frames -> {
                        batchesByPeer.computeIfAbsent(peer, ignored -> new ArrayList<>()).add(frames);
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERED
                        );
                    }
            );
        }

        manager.tick();

        int totalFrames = 0;
        long totalBytes = 0;
        for (List<List<MessageChunkBuffer>> batches : batchesByPeer.values()) {
            for (List<MessageChunkBuffer> batch : batches) {
                totalFrames += batch.size();
                totalBytes += batch.stream().mapToInt(MessageChunkBuffer::dataLength).sum();
                assertTrue(batch.size() <= ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK);
            }
        }
        assertTrue(totalFrames > 0);
        assertTrue(totalFrames <= 10, "frames granted: " + totalFrames);
        assertTrue(
                totalBytes <= 5 * ChunkedMessageManager.MAX_CHUNK_DATA_SIZE,
                "bytes granted: " + totalBytes
        );
    }

    @Test
    void perPeerLimitsStillEnforcedUnderManagerTick() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        ChunkedMessageManager.PreparedMessage prepared =
                ChunkedMessageManager.prepare(messageWithDescription("x".repeat(300_000)), false);
        List<List<MessageChunkBuffer>> batches = new ArrayList<>();
        manager.sendTracked(
                "peer",
                prepared,
                frames -> {
                    batches.add(frames);
                    return CompletableFuture.completedFuture(
                            ChunkedMessageSendResult.DELIVERED
                    );
                }
        );

        int ticks = 0;
        while (batches.stream().mapToInt(List::size).sum() < prepared.frames().size()) {
            assertTrue(ticks++ < 100);
            manager.tick();
        }

        assertTrue(batches.stream().allMatch(batch ->
                batch.size() <= ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK));
        assertTrue(batches.stream().allMatch(batch -> batch.stream()
                .mapToInt(MessageChunkBuffer::dataLength).sum()
                <= ChunkedMessageManager.MAX_BYTES_PER_PEER_TICK));
        assertEquals(
                prepared.retainedBytes(),
                batches.stream()
                        .flatMap(List::stream)
                        .mapToInt(MessageChunkBuffer::dataLength)
                        .sum()
        );
    }

    @Test
    void continuouslyActivePeersAllProgressWithoutStarvation() {
        // Global budget serves one peer per tick; rotation must reach all five.
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK,
                ChunkedMessageManager.MAX_BYTES_PER_PEER_TICK,
                ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK,
                ChunkedMessageManager.MAX_BYTES_PER_PEER_TICK
        );
        Map<String, AtomicInteger> framesByPeer = new java.util.HashMap<>();
        for (String peer : List.of("a", "b", "c", "d", "e")) {
            manager.sendTracked(
                    peer,
                    ChunkedMessageManager.prepare(messageWithDescription("x".repeat(300_000)), false),
                    frames -> {
                        framesByPeer.computeIfAbsent(peer, ignored -> new AtomicInteger())
                                .addAndGet(frames.size());
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERED
                        );
                    }
            );
        }

        for (int tick = 0; tick < 5; tick++) {
            manager.tick();
        }

        for (AtomicInteger frames : framesByPeer.values()) {
            assertTrue(frames.get() > 0, "peer made no progress: " + framesByPeer);
        }
    }

    @Test
    void peersActiveAtTickStartStayAheadOfContinuousNewAdmissions() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE,
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );
        ChunkedMessageManager.PreparedMessage prepared =
                ChunkedMessageManager.prepare(messageWithDescription("one frame"), false);
        Map<String, AtomicInteger> framesByPeer = new java.util.HashMap<>();
        AtomicInteger newcomerId = new AtomicInteger(1);
        AtomicReference<Function<
                List<MessageChunkBuffer>,
                CompletionStage<ChunkedMessageSendResult>
        >> newcomerSender = new AtomicReference<>();
        newcomerSender.set(frames -> {
            String nextPeer = "new-" + newcomerId.getAndIncrement();
            manager.sendTracked(nextPeer, prepared, newcomerSender.get());
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        });

        manager.sendTracked("first", prepared, frames -> {
            framesByPeer.computeIfAbsent("first", ignored -> new AtomicInteger())
                    .addAndGet(frames.size());
            manager.sendTracked("new-0", prepared, newcomerSender.get());
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        });
        manager.sendTracked("waiting", prepared, frames -> {
            framesByPeer.computeIfAbsent("waiting", ignored -> new AtomicInteger())
                    .addAndGet(frames.size());
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        });

        manager.tick();
        manager.tick();

        assertTrue(
                framesByPeer.getOrDefault("waiting", new AtomicInteger()).get() > 0,
                "mid-tick admissions overtook an initially active peer: " + framesByPeer
        );
    }

    @Test
    void peerAdmittedDuringTickWaitsForNextTick() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        List<List<MessageChunkBuffer>> lateBatches = new ArrayList<>();
        AtomicBoolean firstDispatchRan = new AtomicBoolean(false);

        manager.sendTracked(
                "first",
                ChunkedMessageManager.prepare(messageWithDescription("first"), false),
                ignored -> {
                    firstDispatchRan.set(true);
                    // Admitting a peer while the tick is dispatching must not
                    // hand it a grant in the same tick.
                    manager.sendTracked(
                            "late",
                            ChunkedMessageManager.prepare(messageWithDescription("late"), false),
                            frames -> {
                                lateBatches.add(frames);
                                return CompletableFuture.completedFuture(
                                        ChunkedMessageSendResult.DELIVERED
                                );
                            }
                    );
                    return CompletableFuture.completedFuture(
                            ChunkedMessageSendResult.DELIVERED
                    );
                }
        );

        manager.tick();

        assertTrue(firstDispatchRan.get());
        assertTrue(lateBatches.isEmpty());

        manager.tick();

        assertEquals(1, lateBatches.size());
    }

    @Test
    void inFlightBatchIsNotScheduledTwice() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        ChunkedMessageManager.PreparedMessage prepared =
                ChunkedMessageManager.prepare(messageWithDescription("x".repeat(300_000)), false);
        assertTrue(prepared.frames().size() > ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK);
        List<List<MessageChunkBuffer>> batches = new ArrayList<>();
        CompletableFuture<ChunkedMessageSendResult> pendingBatch = new CompletableFuture<>();
        manager.sendTracked("peer", prepared, ignored -> {
            batches.add(ignored);
            return pendingBatch;
        });

        manager.tick();
        manager.tick();
        manager.tick();

        assertEquals(1, batches.size());

        pendingBatch.complete(ChunkedMessageSendResult.DELIVERED);
        manager.tick();

        assertEquals(2, batches.size());
        assertTrue(batches.get(1).size() <= ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK);
        assertEquals(
                prepared.retainedBytes(),
                batches.stream()
                        .flatMap(List::stream)
                        .mapToInt(MessageChunkBuffer::dataLength)
                        .sum()
        );
    }

    @Test
    void sharedBodyReferencesReturnToZeroAfterDelivery() {
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                messageWithDescription("x".repeat(50_000)),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        List<CompletableFuture<ChunkedMessageSendResult>> pendingBatches = new ArrayList<>();
        List<ChunkedMessageDelivery> deliveries = new ArrayList<>();
        for (String peer : List.of("first", "second", "third")) {
            CompletableFuture<ChunkedMessageSendResult> batch = new CompletableFuture<>();
            pendingBatches.add(batch);
            deliveries.add(manager.sendTracked(peer, prepared, ignored -> batch));
        }
        assertEquals(prepared.retainedBytes(), manager.globallyRetainedBytes());

        for (CompletableFuture<ChunkedMessageSendResult> batch : List.copyOf(pendingBatches)) {
            manager.tick();
        }
        for (CompletableFuture<ChunkedMessageSendResult> batch : pendingBatches) {
            batch.complete(ChunkedMessageSendResult.DELIVERED);
        }
        manager.tick();

        for (ChunkedMessageDelivery delivery : deliveries) {
            assertEquals(
                    ChunkedMessageSendResult.DELIVERED,
                    delivery.completion().toCompletableFuture().join()
            );
        }
        assertEquals(0, manager.globallyRetainedBytes());
        assertFalse(manager.hasPending("first"));
        assertFalse(manager.hasPending("second"));
        assertFalse(manager.hasPending("third"));
    }

    @Test
    void failedDispatchDoesNotPreventOtherPeersFromProgressing() {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        ChunkedMessageManager.PreparedMessage failingPrepared = ChunkedMessageManager.prepare(
                messageWithDescription("failing"),
                false
        );
        ChunkedMessageManager.PreparedMessage healthyPrepared = ChunkedMessageManager.prepare(
                messageWithDescription("healthy"),
                false
        );
        ChunkedMessageDelivery failing = manager.sendTracked(
                "failing",
                failingPrepared,
                ignored -> {
                    throw new IllegalStateException("broken dispatch");
                }
        );
        CompletableFuture<ChunkedMessageSendResult> healthyBatch =
                new CompletableFuture<>();
        ChunkedMessageDelivery healthy = manager.sendTracked(
                "healthy",
                healthyPrepared,
                ignored -> healthyBatch
        );

        manager.tick();

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                failing.completion().toCompletableFuture().join()
        );
        assertFalse(manager.hasPending("failing"));
        assertTrue(manager.hasPending("healthy"));
        assertEquals(healthyPrepared.retainedBytes(), manager.globallyRetainedBytes());

        healthyBatch.complete(ChunkedMessageSendResult.DELIVERED);
        assertEquals(
                ChunkedMessageSendResult.DELIVERED,
                healthy.completion().toCompletableFuture().join()
        );
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void blockedPeerDispatchDoesNotBlockAnotherPeer() throws Exception {
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            manager.sendTracked(
                    "blocked",
                    ChunkedMessageManager.prepare(messageWithDescription("blocked"), false),
                    ignored -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERED
                        );
                    }
            );
            Future<?> dispatchingTick = executor.submit((Runnable) () -> manager.tick());
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            // The blocked dispatch runs outside the transport locks, so an
            // unrelated peer can still be admitted.
            ChunkedMessageDelivery unrelated = manager.sendTracked(
                    "unrelated",
                    ChunkedMessageManager.prepare(messageWithDescription("ready"), false),
                    ignored -> CompletableFuture.completedFuture(
                            ChunkedMessageSendResult.DELIVERED
                    )
            );
            assertEquals(ChunkedMessageSendResult.QUEUED, unrelated.admissionResult());

            release.countDown();
            dispatchingTick.get(2, TimeUnit.SECONDS);

            // The peer admitted during the blocked dispatch is served by the
            // next manager tick.
            manager.tick();
            assertEquals(
                    ChunkedMessageSendResult.DELIVERED,
                    unrelated.completion().toCompletableFuture().join()
            );
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void acceptedProgressPreventsIdleExpiry() {
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(100_000)),
                false
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        assertTrue(receiver.receive(
                "peer",
                packets.get(0),
                ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS,
                0
        ).isEmpty());
        assertTrue(receiver.tick(TimeUnit.SECONDS.toNanos(29)).isEmpty());
        assertTrue(receiver.receive(
                "peer",
                packets.get(1),
                ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS,
                TimeUnit.SECONDS.toNanos(29)
        ).isEmpty());

        assertTrue(receiver.tick(TimeUnit.SECONDS.toNanos(58)).isEmpty());
        assertTrue(receiver.hasPending("peer"));
        assertTrue(receiver.globallyRetainedBytes() > 0);
    }

    @Test
    void duplicateChunkDoesNotCountAsProgress() {
        MessageChunkBuffer first = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(40_000)),
                false
        ).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        receiver.receive("peer", first, ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS, 0);
        receiver.receive(
                "peer",
                first,
                ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS,
                TimeUnit.SECONDS.toNanos(29)
        );
        List<ChunkedMessageManager.ReceiveFailure<String>> failures = receiver.tick(
                TimeUnit.SECONDS.toNanos(31)
        );

        assertEquals(1, failures.size());
        assertEquals(
                ChunkedMessageManager.FailureReason.IDLE_TIMEOUT,
                failures.get(0).reason()
        );
    }

    @Test
    void absoluteLifetimeExpiresSlowDripTransfer() {
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(400_000)),
                false
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        for (int i = 0; i <= 10; i++) {
            assertTrue(receiver.receive(
                    "peer",
                    packets.get(i),
                    ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS,
                    TimeUnit.SECONDS.toNanos(29L * i)
            ).isEmpty());
        }
        List<ChunkedMessageManager.ReceiveFailure<String>> failures = receiver.tick(
                ChunkedMessageManager.MAX_LIFETIME_NANOS + TimeUnit.SECONDS.toNanos(1)
        );

        assertEquals(1, failures.size());
        assertEquals(
                ChunkedMessageManager.FailureReason.LIFETIME_TIMEOUT,
                failures.get(0).reason()
        );
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void oneExpiryClearsAllIncomingTransfersForThePeer() {
        MessageChunkBuffer first = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(40_000)),
                false
        ).get(0);
        MessageChunkBuffer second = ChunkedMessageManager.createTransfer(
                modificationWithDescription("x".repeat(40_000)),
                false
        ).get(0);
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        receiver.receive("peer", first, ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS, 0);
        receiver.receive(
                "peer",
                second,
                ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS,
                TimeUnit.SECONDS.toNanos(20)
        );
        List<ChunkedMessageManager.ReceiveFailure<String>> failures = receiver.tick(
                TimeUnit.SECONDS.toNanos(31)
        );

        assertEquals(2, failures.size());
        assertTrue(failures.stream().allMatch(failure ->
                failure.reason() == ChunkedMessageManager.FailureReason.IDLE_TIMEOUT));
        assertEquals(
                java.util.Set.of(
                        ChunkedMessageRegistry.WAYPOINT_DATA.id(),
                        ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
                ),
                failures.stream()
                        .map(ChunkedMessageManager.ReceiveFailure::messageTypeId)
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertFalse(receiver.hasPending("peer"));
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void timeoutFailuresAreDeduplicatedByMessageTypeAndReason() {
        List<MessageChunkBuffer> first = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(40_000)),
                false
        );
        List<MessageChunkBuffer> second = ChunkedMessageManager.createTransfer(
                messageWithDescription("y".repeat(40_000)),
                false
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();

        receiver.receive("peer", first.get(0), ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS, 0);
        receiver.receive("peer", second.get(0), ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS, 0);
        List<ChunkedMessageManager.ReceiveFailure<String>> failures = receiver.tick(
                TimeUnit.SECONDS.toNanos(31)
        );

        assertEquals(1, failures.size());
        assertEquals(ChunkedMessageRegistry.WAYPOINT_DATA.id(), failures.get(0).messageTypeId());
        assertEquals(
                ChunkedMessageManager.FailureReason.IDLE_TIMEOUT,
                failures.get(0).reason()
        );
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void incomingExpiryDoesNotClearOutgoingState() {
        ChunkedMessageManager.PreparedMessage outgoing = ChunkedMessageManager.prepare(
                messageWithDescription("outgoing".repeat(20_000)),
                false
        );
        ChunkedMessageManager<String> manager = new ChunkedMessageManager<>(
                ChunkedMessageManager.MAX_GLOBAL_RETAINED_BYTES,
                1,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE
        );
        manager.send("peer", outgoing, ignored -> {
        });
        MessageChunkBuffer incoming = ChunkedMessageManager.createTransfer(
                modificationWithDescription("incoming".repeat(10_000)),
                false
        ).get(0);
        manager.receive("peer", incoming, ChunkedMessageManager.DEFAULT_RECEIVE_LIMITS, 0);

        assertEquals(1, manager.tick(TimeUnit.SECONDS.toNanos(31)).size());

        assertTrue(manager.hasPending("peer"));
        assertEquals(outgoing.retainedBytes(), manager.globallyRetainedBytes());
        manager.clear("peer");
        assertEquals(0, manager.globallyRetainedBytes());
    }

    @Test
    void checksumAndDecodeFailuresExposeMessageType() {
        MessageChunkBuffer original = ChunkedMessageManager.createTransfer(
                messageWithDescription("checksum"),
                false
        ).get(0);
        MessageChunkBuffer badChecksum = MessageChunkBuffer.chunk(
                original.transferId(),
                original.messageTypeId(),
                0,
                1,
                false,
                original.uncompressedSize(),
                original.checksum() + 1,
                original.data()
        );

        ChunkedMessageManager.ReceiveException checksumFailure = assertThrows(
                ChunkedMessageManager.ReceiveException.class,
                () -> new ChunkedMessageManager<String>().receive("peer", badChecksum)
        );
        assertEquals(ChunkedMessageRegistry.WAYPOINT_DATA.id(), checksumFailure.messageTypeId());
        assertEquals(
                ChunkedMessageManager.FailureReason.MALFORMED,
                checksumFailure.reason()
        );

        byte[] invalidMessage = new byte[]{0};
        CRC32 crc32 = new CRC32();
        crc32.update(invalidMessage);
        MessageChunkBuffer invalidDecode = MessageChunkBuffer.chunk(
                java.util.UUID.randomUUID(),
                ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id(),
                0,
                1,
                false,
                invalidMessage.length,
                (int) crc32.getValue(),
                invalidMessage
        );
        ChunkedMessageManager.ReceiveException decodeFailure = assertThrows(
                ChunkedMessageManager.ReceiveException.class,
                () -> new ChunkedMessageManager<String>().receive("peer", invalidDecode)
        );
        assertEquals(
                ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id(),
                decodeFailure.messageTypeId()
        );
        assertEquals(
                ChunkedMessageManager.FailureReason.DECODE_FAILED,
                decodeFailure.reason()
        );
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
                        malformed
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
                () -> receiver.receive("peer", malformed)
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
    void retainedBytesRemainChargedWhileApplicationIsBlocked() throws Exception {
        List<MessageChunkBuffer> packets = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(60_000)),
                false
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < packets.size() - 1; i++) {
                int sequence = i;
                assertFalse(receiver.receiveAndApply("peer", packets.get(sequence), ignored -> {
                }));
            }
            long bufferedBytes = receiver.globallyRetainedBytes();
            assertTrue(bufferedBytes > 0);

            Future<Boolean> applying = executor.submit(() -> receiver.receiveAndApply(
                    "peer",
                    packets.get(packets.size() - 1),
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
            // The reservation stays charged for the whole application callback.
            assertEquals(bufferedBytes, receiver.globallyRetainedBytes());

            release.countDown();
            assertTrue(applying.get(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.globallyRetainedBytes());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reservationsReleasedAfterSuccessDecodeFailureApplicationFailureAndDisconnect() {
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<ChunkedMessage> applied = new ArrayList<>();

        // Success.
        assertTrue(receiver.receiveAndApply(
                "peer",
                ChunkedMessageManager.createTransfer(messageWithDescription("ok"), false).get(0),
                applied::add
        ));
        assertEquals(1, applied.size());
        assertEquals(0, receiver.globallyRetainedBytes());

        // Decode failure.
        byte[] invalidMessage = new byte[]{0};
        CRC32 crc32 = new CRC32();
        crc32.update(invalidMessage);
        MessageChunkBuffer invalidDecode = MessageChunkBuffer.chunk(
                UUID.randomUUID(),
                ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id(),
                0,
                1,
                false,
                invalidMessage.length,
                (int) crc32.getValue(),
                invalidMessage
        );
        ChunkedMessageManager.ReceiveException decodeFailure = assertThrows(
                ChunkedMessageManager.ReceiveException.class,
                () -> receiver.receiveAndApply("peer", invalidDecode, ignored -> {
                })
        );
        assertEquals(
                ChunkedMessageManager.FailureReason.DECODE_FAILED,
                decodeFailure.reason()
        );
        assertEquals(0, receiver.globallyRetainedBytes());

        // Application failure propagates separately from transport failures.
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                messageWithDescription("boom"),
                false
        );
        IllegalStateException applicationFailure = assertThrows(
                IllegalStateException.class,
                () -> receiver.receiveAndApply("peer", prepared.frames().get(0), ignored -> {
                    throw new IllegalStateException("application failed");
                })
        );
        assertEquals("application failed", applicationFailure.getMessage());
        assertEquals(0, receiver.globallyRetainedBytes());

        // Disconnect releases a partially received transfer exactly once.
        MessageChunkBuffer partial = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(40_000)),
                false
        ).get(0);
        assertFalse(receiver.receiveAndApply("peer", partial, applied::add));
        assertTrue(receiver.globallyRetainedBytes() > 0);
        receiver.clear("peer");
        assertEquals(0, receiver.globallyRetainedBytes());
        receiver.clear("peer");
        assertEquals(0, receiver.globallyRetainedBytes());
    }

    @Test
    void globalBudgetRejectsWhileApplicationBlockedThenAcceptsAfterRelease() throws Exception {
        List<MessageChunkBuffer> first = ChunkedMessageManager.createTransfer(
                messageWithDescription("x".repeat(20_000)),
                false
        );
        List<MessageChunkBuffer> second = ChunkedMessageManager.createTransfer(
                messageWithDescription("blocked budget"),
                false
        );
        long reservation = ChunkedMessageManager.incomingReservation(
                first.get(0).uncompressedSize()
        );
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>(
                reservation,
                ChunkedMessageManager.MAX_FRAMES_PER_PEER_TICK,
                ChunkedMessageManager.MAX_BYTES_PER_PEER_TICK
        );
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> blockedApplication = executor.submit(() -> receiver.receiveAndApply(
                    "first",
                    first.get(0),
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

            // Another peer's reservation cannot fit while the blocked
            // application still holds the global budget.
            assertThrows(
                    ChunkedMessageManager.ReceiveException.class,
                    () -> receiver.receiveAndApply("second", second.get(0), ignored -> {
                    })
            );
            assertEquals(reservation, receiver.globallyRetainedBytes());

            release.countDown();
            assertTrue(blockedApplication.get(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.globallyRetainedBytes());

            // The reservation is accepted once the budget is released again.
            assertTrue(receiver.receiveAndApply("second", second.get(0), ignored -> {
            }));
            assertEquals(0, receiver.globallyRetainedBytes());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void blockingPeerApplicationDoesNotBlockUnrelatedPeer() throws Exception {
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch unrelatedApplied = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> blocked = executor.submit(() -> receiver.receiveAndApply(
                    "blocked",
                    ChunkedMessageManager.createTransfer(messageWithDescription("blocked"), false).get(0),
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

            assertTrue(receiver.receiveAndApply(
                    "unrelated",
                    ChunkedMessageManager.createTransfer(messageWithDescription("ready"), false).get(0),
                    ignored -> unrelatedApplied.countDown()
            ));
            assertEquals(0, unrelatedApplied.getCount());
            assertFalse(blocked.isDone());

            release.countDown();
            assertTrue(blocked.get(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.globallyRetainedBytes());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void samePeerApplicationsRemainOrdered() throws Exception {
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        List<MessageChunkBuffer> first = ChunkedMessageManager.createTransfer(
                messageWithDescription("first".repeat(4_000)),
                false
        );
        List<MessageChunkBuffer> second = ChunkedMessageManager.createTransfer(
                messageWithDescription("second".repeat(4_000)),
                false
        );
        for (int i = 0; i < first.size() - 1; i++) {
            assertFalse(receiver.receiveAndApply("peer", first.get(i), ignored -> {
            }));
        }
        for (int i = 0; i < second.size() - 1; i++) {
            assertFalse(receiver.receiveAndApply("peer", second.get(i), ignored -> {
            }));
        }

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> applyingFirst = executor.submit(() -> receiver.receiveAndApply(
                    "peer",
                    first.get(first.size() - 1),
                    ignored -> {
                        firstEntered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }
            ));
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

            Future<Boolean> applyingSecond = executor.submit(() -> receiver.receiveAndApply(
                    "peer",
                    second.get(second.size() - 1),
                    ignored -> secondEntered.countDown()
            ));

            // Same-peer decode and application are serialized: the second
            // transfer cannot apply before the first handler returns.
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
            assertFalse(applyingSecond.isDone());

            release.countDown();
            assertTrue(applyingFirst.get(2, TimeUnit.SECONDS));
            assertTrue(applyingSecond.get(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.globallyRetainedBytes());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cleanupRacesCannotUnderflowOrDoubleReleaseAccounting() throws Exception {
        ChunkedMessageManager<String> receiver = new ChunkedMessageManager<>();
        MessageChunkBuffer completedFrame = ChunkedMessageManager.createTransfer(
                messageWithDescription("race"),
                false
        ).get(0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> applying = executor.submit(() -> receiver.receiveAndApply(
                    "peer",
                    completedFrame,
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
            long chargedWhileApplying = receiver.globallyRetainedBytes();
            assertTrue(chargedWhileApplying > 0);

            // A malformed frame reusing the completed transfer's id cannot
            // release the still-charged reservation a second time.
            MessageChunkBuffer malformedSameTransfer = MessageChunkBuffer.chunk(
                    completedFrame.transferId(),
                    999_999,
                    0,
                    1,
                    false,
                    8,
                    0,
                    new byte[8]
            );
            assertThrows(
                    ChunkedMessageManager.ReceiveException.class,
                    () -> receiver.receiveAndApply("peer", malformedSameTransfer, ignored -> {
                    })
            );
            assertEquals(chargedWhileApplying, receiver.globallyRetainedBytes());

            // Disconnect while the detached transfer is being applied: the
            // reservation must be released exactly once by the application path.
            receiver.clear("peer");
            assertEquals(chargedWhileApplying, receiver.globallyRetainedBytes());
            release.countDown();
            assertTrue(applying.get(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.globallyRetainedBytes());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
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
