package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Encodes, fragments, queues, and reassembles logical messages.
 *
 * <p>Minecraft's connection already provides reliable ordered delivery, so the
 * chunk layer deliberately has no acknowledgement or retry protocol. Expensive
 * encoding and decoding happens outside transport state locks. Each peer has a
 * small independent state lock, while one short accounting lock bounds the
 * immutable bodies shared by outbound peers and the declared size of inbound
 * transfers.</p>
 *
 * <p>Outbound transmission is decoupled from admission: {@code sendTracked}
 * only queues a transfer and schedules its peer on a round-robin rotation.
 * Each manager-wide {@link #tick()} grants every visited peer at most one
 * batch, bounded by both the per-peer limits and one shared global frame and
 * byte budget, so aggregate throughput stays bounded no matter how many peers
 * are active. Inbound reservations stay charged through reassembly, decoding,
 * and the synchronous application callback, keeping decoded object graphs
 * inside the same retained-byte budget.</p>
 */
public final class ChunkedMessageManager<P> {
    public static final int MAX_CHUNK_DATA_SIZE = 24 * 1_024;
    public static final int MAX_MESSAGE_BYTES = 64 * 1_024 * 1_024;
    public static final int MAX_CHUNKS_PER_TRANSFER =
            (MAX_MESSAGE_BYTES + MAX_CHUNK_DATA_SIZE - 1) / MAX_CHUNK_DATA_SIZE;
    public static final int MAX_ACTIVE_TRANSFERS_PER_PEER = 8;
    public static final int MAX_DECODED_OBJECTS = 1_000_000;
    public static final long MAX_GLOBAL_RETAINED_BYTES = 256L * 1_024L * 1_024L;
    public static final int MAX_FRAMES_PER_PEER_TICK = 8;
    public static final int MAX_BYTES_PER_PEER_TICK =
            MAX_FRAMES_PER_PEER_TICK * MAX_CHUNK_DATA_SIZE;
    public static final int MAX_FRAMES_PER_TICK = 32;
    public static final int MAX_BYTES_PER_TICK =
            MAX_FRAMES_PER_TICK * MAX_CHUNK_DATA_SIZE;
    static final long IDLE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    static final long MAX_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(5);
    public static final ReceiveLimits DEFAULT_RECEIVE_LIMITS = new ReceiveLimits(
            MAX_MESSAGE_BYTES,
            MAX_DECODED_OBJECTS
    );

    private final ConcurrentHashMap<P, PeerState> peers = new ConcurrentHashMap<>();
    private final ReentrantLock accountingLock = new ReentrantLock();
    private final IdentityHashMap<PreparedMessage, Integer> outgoingBodyReferences =
            new IdentityHashMap<>();
    private final ReentrantLock schedulingLock = new ReentrantLock();
    private final ArrayDeque<ScheduledPeer<P>> scheduledPeers = new ArrayDeque<>();
    private final Set<PeerState> scheduledPeerSet = Collections.newSetFromMap(
            new IdentityHashMap<>()
    );
    private final long maxGlobalRetainedBytes;
    private final int maxFramesPerPeerTick;
    private final int maxBytesPerPeerTick;
    private final int maxFramesPerTick;
    private final long maxBytesPerTick;
    private long globallyRetainedBytes;

    public ChunkedMessageManager() {
        this(
                MAX_GLOBAL_RETAINED_BYTES,
                MAX_FRAMES_PER_PEER_TICK,
                MAX_BYTES_PER_PEER_TICK
        );
    }

    ChunkedMessageManager(
            long maxGlobalRetainedBytes,
            int maxFramesPerPeerTick,
            int maxBytesPerPeerTick
    ) {
        this(
                maxGlobalRetainedBytes,
                maxFramesPerPeerTick,
                maxBytesPerPeerTick,
                MAX_FRAMES_PER_TICK,
                MAX_BYTES_PER_TICK
        );
    }

    ChunkedMessageManager(
            long maxGlobalRetainedBytes,
            int maxFramesPerPeerTick,
            int maxBytesPerPeerTick,
            int maxFramesPerTick,
            long maxBytesPerTick
    ) {
        if (maxGlobalRetainedBytes <= 0
                || maxFramesPerPeerTick <= 0
                || maxBytesPerPeerTick <= 0
                || maxFramesPerTick <= 0
                || maxBytesPerTick <= 0) {
            throw new IllegalArgumentException("Chunked-message limits must be positive");
        }
        this.maxGlobalRetainedBytes = maxGlobalRetainedBytes;
        this.maxFramesPerPeerTick = maxFramesPerPeerTick;
        this.maxBytesPerPeerTick = maxBytesPerPeerTick;
        this.maxFramesPerTick = maxFramesPerTick;
        this.maxBytesPerTick = maxBytesPerTick;
    }

    public ChunkedMessageSendResult send(
            P peer,
            ChunkedMessage message,
            boolean compressionEnabled,
            Consumer<List<MessageChunkBuffer>> batchSender
    ) {
        return this.send(peer, prepare(message, compressionEnabled), batchSender);
    }

    public ChunkedMessageDelivery sendTracked(
            P peer,
            ChunkedMessage message,
            boolean compressionEnabled,
            Function<List<MessageChunkBuffer>, CompletionStage<ChunkedMessageSendResult>> batchSender
    ) {
        return this.sendTracked(peer, prepare(message, compressionEnabled), batchSender);
    }

    public ChunkedMessageSendResult send(
            P peer,
            PreparedMessage message,
            Consumer<List<MessageChunkBuffer>> batchSender
    ) {
        ChunkedMessageDelivery delivery = this.sendTracked(
                peer,
                message,
                frames -> {
                    try {
                        batchSender.accept(frames);
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERED
                        );
                    } catch (RuntimeException exception) {
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERY_FAILED
                        );
                    }
                }
        );
        CompletableFuture<ChunkedMessageSendResult> completion =
                delivery.completion().toCompletableFuture();
        if (completion.isDone()) {
            ChunkedMessageSendResult result = completion.getNow(
                    ChunkedMessageSendResult.DELIVERY_FAILED
            );
            if (!result.delivered()) {
                return result;
            }
        }
        return delivery.admissionResult();
    }

    public ChunkedMessageDelivery sendTracked(
            P peer,
            PreparedMessage message,
            Function<List<MessageChunkBuffer>, CompletionStage<ChunkedMessageSendResult>> batchSender
    ) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(batchSender, "batchSender");
        CompletableFuture<ChunkedMessageSendResult> completion = new CompletableFuture<>();
        PeerState state;
        while (true) {
            state = this.peers.computeIfAbsent(peer, ignored -> new PeerState());
            state.stateLock.lock();
            try {
                if (state.closed) {
                    this.peers.remove(peer, state);
                    continue;
                }
                if (state.outgoing.size() >= MAX_ACTIVE_TRANSFERS_PER_PEER
                        || state.outgoingRetainedBytes + message.retainedBytes
                        > MAX_MESSAGE_BYTES) {
                    return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.PEER_BUSY);
                }
                if (!this.retainOutgoingBody(message)) {
                    return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.PEER_BUSY);
                }
                state.outgoing.addLast(new OutgoingTransfer(
                        message,
                        batchSender,
                        completion
                ));
                state.outgoingRetainedBytes += message.retainedBytes;
                break;
            } finally {
                state.stateLock.unlock();
            }
        }
        this.enqueuePeer(peer, state);
        return ChunkedMessageDelivery.queued(completion);
    }

    /**
     * Buffers one inbound frame and synchronously applies the decoded message
     * while its reassembly reservation is still charged.
     *
     * @return true when the packet completed a transfer and the handler was
     *         invoked; false when the frame was buffered for an incomplete
     *         transfer
     */
    public boolean receiveAndApply(
            P peer,
            MessageChunkBuffer packet,
            Consumer<ChunkedMessage> handler
    ) {
        return this.receiveAndApply(peer, packet, DEFAULT_RECEIVE_LIMITS, handler);
    }

    public boolean receiveAndApply(
            P peer,
            MessageChunkBuffer packet,
            ReceiveLimits limits,
            Consumer<ChunkedMessage> handler
    ) {
        return this.receiveAndApply(peer, packet, limits, System.nanoTime(), handler);
    }

    boolean receiveAndApply(
            P peer,
            MessageChunkBuffer packet,
            ReceiveLimits limits,
            long now,
            Consumer<ChunkedMessage> handler
    ) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(handler, "handler");
        try {
            validateChunk(packet, limits);
        } catch (RuntimeException exception) {
            IncomingTransfer failed = this.removeIncomingTransfer(peer, packet.transferId());
            if (failed != null) {
                throw new ReceiveException(
                        failed.messageTypeId,
                        FailureReason.MALFORMED,
                        failed.transferId,
                        exception
                );
            }
            throw receiveException(packet, FailureReason.MALFORMED, exception);
        }
        PeerState state;
        IncomingTransfer completed;
        long applicationSequence;
        while (true) {
            state = this.peers.computeIfAbsent(peer, ignored -> new PeerState());
            state.stateLock.lock();
            try {
                if (state.closed) {
                    this.peers.remove(peer, state);
                    continue;
                }
                try {
                    IncomingTransfer transfer = state.incoming.get(packet.transferId());
                    if (transfer == null) {
                        if (state.incoming.size() >= MAX_ACTIVE_TRANSFERS_PER_PEER) {
                            throw new IllegalStateException(
                                    "Too many incoming chunked-message transfers for peer"
                            );
                        }
                        if (state.incomingDeclaredBytes + packet.uncompressedSize()
                                > MAX_MESSAGE_BYTES) {
                            throw new IllegalStateException(
                                    "Too many incoming chunked-message bytes retained for peer"
                            );
                        }
                        long reservedBytes = incomingReservation(packet.uncompressedSize());
                        if (!this.reserveIncomingBytes(reservedBytes)) {
                            throw new IllegalStateException(
                                    "Global chunked-message retained-byte budget exhausted"
                            );
                        }
                        transfer = new IncomingTransfer(
                                packet,
                                limits,
                                now,
                                reservedBytes
                        );
                        state.incoming.put(packet.transferId(), transfer);
                        state.incomingDeclaredBytes += transfer.uncompressedSize;
                    } else {
                        transfer.validateMetadata(packet, limits);
                    }
                    transfer.accept(packet, now);
                    if (!transfer.complete()) {
                        return false;
                    }
                    // Detach the finished transfer; its reservation stays charged
                    // through decoding and application and is released exactly once
                    // by the finally block below.
                    state.incoming.remove(packet.transferId());
                    state.incomingDeclaredBytes -= transfer.uncompressedSize;
                    completed = transfer;
                    applicationSequence = state.nextCompletedApplicationSequence++;
                    break;
                } catch (RuntimeException exception) {
                    IncomingTransfer failed = state.incoming.remove(packet.transferId());
                    if (failed != null) {
                        state.incomingDeclaredBytes -= failed.uncompressedSize;
                        this.releaseIncomingBytes(failed.reservedBytes);
                    }
                    if (exception instanceof ReceiveException receiveException) {
                        throw receiveException;
                    }
                    if (failed != null) {
                        throw new ReceiveException(
                                failed.messageTypeId,
                                FailureReason.MALFORMED,
                                failed.transferId,
                                exception
                        );
                    }
                    throw receiveException(packet, FailureReason.MALFORMED, exception);
                }
            } finally {
                state.stateLock.unlock();
            }
        }

        state.decodeLock.lock();
        try {
            while (applicationSequence != state.nextApplicationSequence) {
                state.decodeOrder.awaitUninterruptibly();
            }
            byte[] bytes;
            try {
                bytes = completed.reassemble();
            } catch (RuntimeException exception) {
                throw new ReceiveException(
                        completed.messageTypeId,
                        FailureReason.MALFORMED,
                        completed.transferId,
                        exception
                );
            }
            ChunkedMessage message;
            try {
                message = decodeMessage(completed.messageTypeId, bytes, completed.limits);
            } catch (RuntimeException exception) {
                throw new ReceiveException(
                        completed.messageTypeId,
                        FailureReason.DECODE_FAILED,
                        completed.transferId,
                        exception
                );
            }
            // Application exceptions propagate separately from transport
            // ReceiveExceptions; the reservation is still charged until the
            // handler returns or throws.
            handler.accept(message);
        } finally {
            state.nextApplicationSequence++;
            state.decodeOrder.signalAll();
            state.decodeLock.unlock();
            this.releaseIncomingBytes(completed.reservedBytes);
        }
        return true;
    }

    /** Collecting variant used only by focused codec tests. */
    List<ChunkedMessage> receive(
            P peer,
            MessageChunkBuffer packet
    ) {
        return this.receive(peer, packet, DEFAULT_RECEIVE_LIMITS);
    }

    List<ChunkedMessage> receive(
            P peer,
            MessageChunkBuffer packet,
            ReceiveLimits limits
    ) {
        return this.receive(peer, packet, limits, System.nanoTime());
    }

    List<ChunkedMessage> receive(
            P peer,
            MessageChunkBuffer packet,
            ReceiveLimits limits,
            long now
    ) {
        List<ChunkedMessage> received = new ArrayList<>(1);
        this.receiveAndApply(peer, packet, limits, now, received::add);
        return received;
    }

    public List<ReceiveFailure<P>> tick() {
        return this.tick(System.nanoTime());
    }

    public boolean hasPending(P peer) {
        PeerState state = this.peers.get(peer);
        if (state == null) {
            return false;
        }
        state.stateLock.lock();
        try {
            return !state.closed
                    && (!state.outgoing.isEmpty() || !state.incoming.isEmpty());
        } finally {
            state.stateLock.unlock();
        }
    }

    /**
     * Expires stale inbound transfers for every peer, then dispatches one
     * outbound grant round across the active peers under this manager's global
     * frame and byte budgets using round-robin order.
     */
    List<ReceiveFailure<P>> tick(long now) {
        List<ScheduledPeer<P>> scheduled = this.pollScheduledPeers();
        List<ReceiveFailure<P>> failures = new ArrayList<>();
        for (Map.Entry<P, PeerState> entry : this.peers.entrySet()) {
            failures.addAll(this.expireIncoming(entry.getKey(), entry.getValue(), now));
        }
        this.dispatchOutboundGrants(scheduled);
        return List.copyOf(failures);
    }

    private void dispatchOutboundGrants(List<ScheduledPeer<P>> scheduled) {
        long remainingFrames = this.maxFramesPerTick;
        long remainingBytes = this.maxBytesPerTick;
        Set<PeerState> grantedStates = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (ScheduledPeer<P> scheduledPeer : scheduled) {
                P peer = scheduledPeer.peer();
                PeerState state = scheduledPeer.state();
                if (this.peers.get(peer) != state) {
                    continue;
                }
                if (remainingFrames > 0 && remainingBytes > 0) {
                    int grantFrames = (int) Math.min(
                            remainingFrames,
                            this.maxFramesPerPeerTick
                    );
                    long grantBytes = Math.min(remainingBytes, this.maxBytesPerPeerTick);
                    Emitted emittedBatch = this.emitPendingBatch(
                            peer,
                            state,
                            grantFrames,
                            grantBytes
                    );
                    remainingFrames -= emittedBatch.frames();
                    remainingBytes -= emittedBatch.bytes();
                    if (emittedBatch.frames() > 0) {
                        grantedStates.add(state);
                    }
                }
            }
        } finally {
            this.finishScheduledPeers(scheduled, grantedStates);
        }
    }

    public void clear(P peer) {
        this.clear(peer, null, ChunkedMessageSendResult.DELIVERY_FAILED);
    }

    private void clear(
            P peer,
            PeerState expectedState,
            ChunkedMessageSendResult result
    ) {
        PeerState state;
        if (expectedState == null) {
            state = this.peers.remove(peer);
        } else if (this.peers.remove(peer, expectedState)) {
            state = expectedState;
        } else {
            state = null;
        }
        if (state == null) {
            return;
        }
        this.dequeuePeer(state);
        List<OutgoingTransfer> outgoingTransfers = new ArrayList<>();
        long incomingBytes = 0;
        state.stateLock.lock();
        try {
            state.closed = true;
            for (OutgoingTransfer transfer : state.outgoing) {
                outgoingTransfers.add(transfer);
            }
            state.outgoing.clear();
            state.outgoingRetainedBytes = 0;
            for (IncomingTransfer transfer : state.incoming.values()) {
                incomingBytes += transfer.reservedBytes;
            }
            state.incoming.clear();
            state.incomingDeclaredBytes = 0;
            state.inFlight = null;
        } finally {
            state.stateLock.unlock();
        }
        for (OutgoingTransfer transfer : outgoingTransfers) {
            this.releaseOutgoingBody(transfer.message);
            transfer.completion.complete(result);
        }
        this.releaseIncomingBytes(incomingBytes);
    }

    public void clearAll() {
        for (P peer : List.copyOf(this.peers.keySet())) {
            this.clear(peer);
        }
    }

    public static PreparedMessage prepare(
            ChunkedMessage message,
            boolean compressionEnabled
    ) {
        Objects.requireNonNull(message, "message");
        byte[] uncompressed = encodeMessage(message);
        byte[] compressed = compressionEnabled ? compress(uncompressed) : uncompressed;
        boolean useCompression = compressionEnabled && compressed.length < uncompressed.length;
        byte[] transmitted = useCompression ? compressed : uncompressed;
        int chunkCount = Math.max(
                1,
                (transmitted.length + MAX_CHUNK_DATA_SIZE - 1) / MAX_CHUNK_DATA_SIZE
        );
        if (chunkCount > MAX_CHUNKS_PER_TRANSFER) {
            throw new MessageEncodingException("Chunked message has too many chunks");
        }
        CRC32 crc32 = new CRC32();
        crc32.update(uncompressed);
        int checksum = (int) crc32.getValue();
        UUID transferId = UUID.randomUUID();
        List<MessageChunkBuffer> frames = new ArrayList<>(chunkCount);
        for (int sequence = 0; sequence < chunkCount; sequence++) {
            int from = sequence * MAX_CHUNK_DATA_SIZE;
            int to = Math.min(from + MAX_CHUNK_DATA_SIZE, transmitted.length);
            frames.add(MessageChunkBuffer.chunk(
                    transferId,
                    message.getType().id(),
                    sequence,
                    chunkCount,
                    useCompression,
                    uncompressed.length,
                    checksum,
                    Arrays.copyOfRange(transmitted, from, to)
            ));
        }
        return new PreparedMessage(frames, transmitted.length);
    }

    public static List<MessageChunkBuffer> createTransfer(
            ChunkedMessage message,
            boolean compressionEnabled
    ) {
        return prepare(message, compressionEnabled).frames;
    }

    public static void validateEncodable(ChunkedMessage message) {
        encodeMessage(message);
    }

    long globallyRetainedBytes() {
        this.accountingLock.lock();
        try {
            return this.globallyRetainedBytes;
        } finally {
            this.accountingLock.unlock();
        }
    }

    private Emitted emitPendingBatch(
            P peer,
            PeerState state,
            int maxFrames,
            long maxBytes
    ) {
        PendingBatch pending;
        state.stateLock.lock();
        try {
            if (state.closed || state.inFlight != null) {
                return Emitted.NONE;
            }
            OutgoingTransfer transfer = state.outgoing.peekFirst();
            if (transfer == null) {
                return Emitted.NONE;
            }
            int start = transfer.nextFrame;
            int end = start;
            long bytes = 0;
            List<MessageChunkBuffer> frames = transfer.message.frames;
            while (end < frames.size() && end - start < maxFrames) {
                int frameBytes = frames.get(end).dataLength();
                if (bytes + frameBytes > maxBytes) {
                    break;
                }
                bytes += frameBytes;
                end++;
            }
            if (end == start) {
                return Emitted.NONE;
            }
            pending = new PendingBatch(
                    transfer,
                    end,
                    List.copyOf(frames.subList(start, end)),
                    bytes
            );
            state.inFlight = pending;
        } finally {
            state.stateLock.unlock();
        }

        CompletionStage<ChunkedMessageSendResult> delivery;
        try {
            delivery = Objects.requireNonNull(
                    pending.transfer.batchSender.apply(pending.frames),
                    "batch delivery"
            );
        } catch (RuntimeException exception) {
            this.clear(peer, state, ChunkedMessageSendResult.DELIVERY_FAILED);
            return new Emitted(pending.frames.size(), pending.byteLength);
        }
        delivery.whenComplete((result, exception) -> this.completeBatch(
                peer,
                state,
                pending,
                exception == null ? result : ChunkedMessageSendResult.DELIVERY_FAILED
        ));
        return new Emitted(pending.frames.size(), pending.byteLength);
    }

    private void completeBatch(
            P peer,
            PeerState state,
            PendingBatch pending,
            ChunkedMessageSendResult result
    ) {
        if (result == null || !result.delivered()) {
            this.clear(
                    peer,
                    state,
                    result == null || result.queued()
                            ? ChunkedMessageSendResult.DELIVERY_FAILED
                            : result
            );
            return;
        }
        PreparedMessage completedBody = null;
        CompletableFuture<ChunkedMessageSendResult> completedTransfer = null;
        state.stateLock.lock();
        try {
            if (state.closed || state.inFlight != pending) {
                return;
            }
            pending.transfer.nextFrame = pending.nextFrame;
            if (pending.transfer.nextFrame == pending.transfer.message.frames.size()) {
                OutgoingTransfer removed = state.outgoing.removeFirst();
                if (removed != pending.transfer) {
                    throw new IllegalStateException("Chunked-message outbound queue changed unexpectedly");
                }
                state.outgoingRetainedBytes -= removed.message.retainedBytes;
                completedBody = removed.message;
                completedTransfer = removed.completion;
            }
            state.inFlight = null;
        } finally {
            state.stateLock.unlock();
        }
        if (completedBody != null) {
            this.releaseOutgoingBody(completedBody);
            completedTransfer.complete(ChunkedMessageSendResult.DELIVERED);
        }
    }

    private List<ReceiveFailure<P>> expireIncoming(P peer, PeerState state, long now) {
        List<IncomingTransfer> clearedTransfers;
        FailureReason fallbackReason = null;
        long releasedBytes = 0;
        state.stateLock.lock();
        try {
            if (state.closed) {
                return List.of();
            }
            for (IncomingTransfer transfer : state.incoming.values()) {
                FailureReason reason = transfer.expirationReason(now);
                if (reason == FailureReason.LIFETIME_TIMEOUT) {
                    fallbackReason = reason;
                    break;
                }
                if (reason != null) {
                    fallbackReason = reason;
                }
            }
            if (fallbackReason == null) {
                return List.of();
            }
            clearedTransfers = List.copyOf(state.incoming.values());
            state.incoming.clear();
            for (IncomingTransfer transfer : clearedTransfers) {
                state.incomingDeclaredBytes -= transfer.uncompressedSize;
                releasedBytes += transfer.reservedBytes;
            }
        } finally {
            state.stateLock.unlock();
        }
        this.releaseIncomingBytes(releasedBytes);

        Map<FailureKey, ReceiveFailure<P>> failures = new LinkedHashMap<>();
        for (IncomingTransfer transfer : clearedTransfers) {
            FailureReason reason = transfer.expirationReason(now);
            if (reason == null) {
                reason = fallbackReason;
            }
            FailureKey key = new FailureKey(transfer.messageTypeId, reason);
            failures.putIfAbsent(key, new ReceiveFailure<>(
                    peer,
                    transfer.messageTypeId,
                    reason,
                    Optional.of(transfer.transferId)
            ));
        }
        return List.copyOf(failures.values());
    }

    private static ReceiveException receiveException(
            MessageChunkBuffer packet,
            FailureReason reason,
            RuntimeException cause
    ) {
        return new ReceiveException(
                packet.messageTypeId(),
                reason,
                packet.transferId(),
                cause
        );
    }

    private IncomingTransfer removeIncomingTransfer(P peer, UUID transferId) {
        PeerState state = this.peers.get(peer);
        if (state == null) {
            return null;
        }
        IncomingTransfer removed;
        state.stateLock.lock();
        try {
            if (state.closed) {
                return null;
            }
            removed = state.incoming.remove(transferId);
            if (removed != null) {
                state.incomingDeclaredBytes -= removed.uncompressedSize;
            }
        } finally {
            state.stateLock.unlock();
        }
        if (removed != null) {
            this.releaseIncomingBytes(removed.reservedBytes);
        }
        return removed;
    }

    private boolean retainOutgoingBody(PreparedMessage message) {
        this.accountingLock.lock();
        try {
            Integer references = this.outgoingBodyReferences.get(message);
            if (references == null) {
                if (this.globallyRetainedBytes + message.retainedBytes
                        > this.maxGlobalRetainedBytes) {
                    return false;
                }
                this.globallyRetainedBytes += message.retainedBytes;
                references = 0;
            }
            this.outgoingBodyReferences.put(message, references + 1);
            return true;
        } finally {
            this.accountingLock.unlock();
        }
    }

    private void releaseOutgoingBody(PreparedMessage message) {
        this.accountingLock.lock();
        try {
            Integer references = this.outgoingBodyReferences.get(message);
            if (references == null) {
                return;
            }
            if (references == 1) {
                this.outgoingBodyReferences.remove(message);
                this.globallyRetainedBytes -= message.retainedBytes;
            } else {
                this.outgoingBodyReferences.put(message, references - 1);
            }
        } finally {
            this.accountingLock.unlock();
        }
    }

    private boolean reserveIncomingBytes(long bytes) {
        this.accountingLock.lock();
        try {
            if (this.globallyRetainedBytes + bytes > this.maxGlobalRetainedBytes) {
                return false;
            }
            this.globallyRetainedBytes += bytes;
            return true;
        } finally {
            this.accountingLock.unlock();
        }
    }

    private void releaseIncomingBytes(long bytes) {
        if (bytes == 0) {
            return;
        }
        this.accountingLock.lock();
        try {
            this.globallyRetainedBytes -= bytes;
            if (this.globallyRetainedBytes < 0) {
                throw new IllegalStateException("Chunked-message retained-byte accounting underflow");
            }
        } finally {
            this.accountingLock.unlock();
        }
    }

    private static void validateChunk(MessageChunkBuffer packet, ReceiveLimits limits) {
        ChunkedMessageRegistry.get(packet.messageTypeId());
        if (packet.chunkCount() <= 0 || packet.chunkCount() > MAX_CHUNKS_PER_TRANSFER) {
            throw new IllegalArgumentException("Invalid message chunk count: " + packet.chunkCount());
        }
        if (packet.sequence() < 0 || packet.sequence() >= packet.chunkCount()) {
            throw new IllegalArgumentException("Invalid message chunk sequence: " + packet.sequence());
        }
        if (packet.uncompressedSize() < 0
                || packet.uncompressedSize() > limits.maxMessageBytes()) {
            throw new IllegalArgumentException("Invalid chunked-message size: " + packet.uncompressedSize());
        }
        int dataLength = packet.dataLength();
        if (dataLength > MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid message chunk length: " + dataLength);
        }
        if (packet.uncompressedSize() > 0 && dataLength == 0) {
            throw new IllegalArgumentException("Non-empty logical message has an empty chunk");
        }
        if (packet.sequence() < packet.chunkCount() - 1
                && dataLength != MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Non-final message chunk has invalid length");
        }
    }

    static long incomingReservation(int uncompressedSize) {
        return Math.multiplyExact((long) uncompressedSize, 3L);
    }

    private static byte[] encodeMessage(ChunkedMessage message) {
        ByteBuf buffer = Unpooled.buffer(Math.min(256, MAX_MESSAGE_BYTES), MAX_MESSAGE_BYTES);
        try {
            ChunkedMessageRegistry.encode(buffer, message, new EncodingContext(MAX_MESSAGE_BYTES));
            byte[] result = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), result);
            return result;
        } catch (MessageEncodingException exception) {
            throw exception;
        } catch (IndexOutOfBoundsException exception) {
            throw new MessageEncodingException(
                    "Chunked message exceeds " + MAX_MESSAGE_BYTES + " bytes",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new MessageEncodingException("Failed to encode chunked message", exception);
        } finally {
            buffer.release();
        }
    }

    private static ChunkedMessage decodeMessage(
            int typeId,
            byte[] bytes,
            ReceiveLimits limits
    ) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            ChunkedMessage result = ChunkedMessageRegistry.decode(
                    typeId,
                    buffer,
                    new DecodingContext(
                            limits.maxMessageBytes(),
                            limits.maxDecodedObjects()
                    )
            );
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Chunked message has trailing bytes");
            }
            return result;
        } catch (IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException(
                    "Chunked message ended before decoding completed",
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Failed to decode chunked message", exception);
        } finally {
            buffer.release();
        }
    }

    private static byte[] compress(byte[] bytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
                deflater.write(bytes);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new MessageEncodingException("Failed to compress chunked message", exception);
        }
    }

    private static byte[] decompress(byte[] bytes, int expectedSize) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(expectedSize);
            try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
                byte[] buffer = new byte[8 * 1_024];
                int read;
                while ((read = inflater.read(buffer)) != -1) {
                    if (output.size() + read > expectedSize) {
                        throw new IllegalArgumentException(
                                "Decompressed chunked message exceeds declared size"
                        );
                    }
                    output.write(buffer, 0, read);
                }
            }
            byte[] result = output.toByteArray();
            if (result.length != expectedSize) {
                throw new IllegalArgumentException(
                        "Decompressed chunked message does not match declared size"
                );
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decompress chunked message", exception);
        }
    }

    public static final class PreparedMessage {
        private final List<MessageChunkBuffer> frames;
        private final int retainedBytes;

        private PreparedMessage(List<MessageChunkBuffer> frames, int retainedBytes) {
            this.frames = List.copyOf(frames);
            this.retainedBytes = retainedBytes;
        }

        public List<MessageChunkBuffer> frames() {
            return this.frames;
        }

        public int retainedBytes() {
            return this.retainedBytes;
        }
    }

    public record ReceiveLimits(int maxMessageBytes, int maxDecodedObjects) {
        public ReceiveLimits {
            if (maxMessageBytes <= 0 || maxMessageBytes > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException(
                        "Receive byte limit must be between 1 and " + MAX_MESSAGE_BYTES
                );
            }
            if (maxDecodedObjects <= 0 || maxDecodedObjects > MAX_DECODED_OBJECTS) {
                throw new IllegalArgumentException(
                        "Receive object limit must be between 1 and " + MAX_DECODED_OBJECTS
                );
            }
        }
    }

    public enum FailureReason {
        IDLE_TIMEOUT,
        LIFETIME_TIMEOUT,
        MALFORMED,
        DECODE_FAILED
    }

    public record ReceiveFailure<P>(
            P peer,
            int messageTypeId,
            FailureReason reason,
            Optional<UUID> transferId
    ) {
        public ReceiveFailure {
            Objects.requireNonNull(peer, "peer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(transferId, "transferId");
        }
    }

    public static final class ReceiveException extends IllegalArgumentException {
        private final int messageTypeId;
        private final FailureReason reason;
        private final UUID transferId;

        private ReceiveException(
                int messageTypeId,
                FailureReason reason,
                UUID transferId,
                Throwable cause
        ) {
            super(
                    "Failed to receive chunked message type " + messageTypeId
                            + " (" + reason + ")",
                    cause
            );
            this.messageTypeId = messageTypeId;
            this.reason = Objects.requireNonNull(reason, "reason");
            this.transferId = transferId;
        }

        public int messageTypeId() {
            return this.messageTypeId;
        }

        public FailureReason reason() {
            return this.reason;
        }

        public Optional<UUID> transferId() {
            return Optional.ofNullable(this.transferId);
        }
    }

    private static final class PeerState {
        private final ReentrantLock stateLock = new ReentrantLock();
        private final ReentrantLock decodeLock = new ReentrantLock();
        private final Condition decodeOrder = this.decodeLock.newCondition();
        private final ArrayDeque<OutgoingTransfer> outgoing = new ArrayDeque<>();
        private final Map<UUID, IncomingTransfer> incoming = new java.util.HashMap<>();
        private long outgoingRetainedBytes;
        private long incomingDeclaredBytes;
        private long nextCompletedApplicationSequence;
        private long nextApplicationSequence;
        private PendingBatch inFlight;
        private boolean closed;
    }

    private static final class OutgoingTransfer {
        private final PreparedMessage message;
        private final Function<
                List<MessageChunkBuffer>,
                CompletionStage<ChunkedMessageSendResult>
        > batchSender;
        private final CompletableFuture<ChunkedMessageSendResult> completion;
        private int nextFrame;

        private OutgoingTransfer(
                PreparedMessage message,
                Function<
                        List<MessageChunkBuffer>,
                        CompletionStage<ChunkedMessageSendResult>
                > batchSender,
                CompletableFuture<ChunkedMessageSendResult> completion
        ) {
            this.message = message;
            this.batchSender = batchSender;
            this.completion = completion;
        }
    }

    private record PendingBatch(
            OutgoingTransfer transfer,
            int nextFrame,
            List<MessageChunkBuffer> frames,
            long byteLength
    ) {
    }

    private record Emitted(int frames, long bytes) {
        private static final Emitted NONE = new Emitted(0, 0);
    }

    private void enqueuePeer(P peer, PeerState state) {
        this.schedulingLock.lock();
        try {
            if (this.scheduledPeerSet.add(state)) {
                this.scheduledPeers.addLast(new ScheduledPeer<>(peer, state));
            }
        } finally {
            this.schedulingLock.unlock();
        }
    }

    private void dequeuePeer(PeerState state) {
        this.schedulingLock.lock();
        try {
            if (this.scheduledPeerSet.remove(state)) {
                this.scheduledPeers.removeIf(scheduledPeer ->
                        scheduledPeer.state() == state);
            }
        } finally {
            this.schedulingLock.unlock();
        }
    }

    /**
     * Removes the currently queued peers from the rotation. Peers admitted
     * while this tick runs stay queued and begin on the next tick.
     */
    private List<ScheduledPeer<P>> pollScheduledPeers() {
        this.schedulingLock.lock();
        try {
            int count = this.scheduledPeers.size();
            List<ScheduledPeer<P>> scheduled = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ScheduledPeer<P> scheduledPeer = this.scheduledPeers.pollFirst();
                if (scheduledPeer == null) {
                    break;
                }
                scheduled.add(scheduledPeer);
            }
            return scheduled;
        } finally {
            this.schedulingLock.unlock();
        }
    }

    /**
     * Restores unfinished peers ahead of peers admitted after this tick's
     * snapshot. Membership remains generation-specific for the whole tick, so
     * admissions for an already-active state cannot create duplicates.
     */
    private void finishScheduledPeers(
            List<ScheduledPeer<P>> scheduled,
            Set<PeerState> grantedStates
    ) {
        this.schedulingLock.lock();
        try {
            List<ScheduledPeer<P>> skipped = new ArrayList<>(scheduled.size());
            List<ScheduledPeer<P>> granted = new ArrayList<>(scheduled.size());
            for (ScheduledPeer<P> scheduledPeer : scheduled) {
                if (!this.scheduledPeerSet.contains(scheduledPeer.state())
                        || this.peers.get(scheduledPeer.peer()) != scheduledPeer.state()
                        || !this.hasPendingOutboundWork(scheduledPeer.state())) {
                    this.scheduledPeerSet.remove(scheduledPeer.state());
                } else if (grantedStates.contains(scheduledPeer.state())) {
                    granted.add(scheduledPeer);
                } else {
                    skipped.add(scheduledPeer);
                }
            }
            skipped.addAll(granted);
            // Initial peers return ahead of peers admitted after the snapshot.
            for (int index = skipped.size() - 1; index >= 0; index--) {
                ScheduledPeer<P> scheduledPeer = skipped.get(index);
                if (this.scheduledPeerSet.contains(scheduledPeer.state())) {
                    this.scheduledPeers.addFirst(scheduledPeer);
                }
            }
        } finally {
            this.schedulingLock.unlock();
        }
    }

    private boolean hasPendingOutboundWork(PeerState state) {
        state.stateLock.lock();
        try {
            return !state.closed
                    && (!state.outgoing.isEmpty() || state.inFlight != null);
        } finally {
            state.stateLock.unlock();
        }
    }

    private record FailureKey(int messageTypeId, FailureReason reason) {
    }

    private record ScheduledPeer<P>(P peer, PeerState state) {
    }

    private static final class IncomingTransfer {
        private final UUID transferId;
        private final int messageTypeId;
        private final int chunkCount;
        private final boolean compressed;
        private final int uncompressedSize;
        private final int checksum;
        private final ReceiveLimits limits;
        private final long reservedBytes;
        private final byte[][] chunks;
        private final long createdNanos;
        private long lastProgressNanos;
        private int receivedChunks;
        private int retainedBytes;

        private IncomingTransfer(
                MessageChunkBuffer first,
                ReceiveLimits limits,
                long now,
                long reservedBytes
        ) {
            this.transferId = first.transferId();
            this.messageTypeId = first.messageTypeId();
            this.chunkCount = first.chunkCount();
            this.compressed = first.compressed();
            this.uncompressedSize = first.uncompressedSize();
            this.checksum = first.checksum();
            this.limits = limits;
            this.reservedBytes = reservedBytes;
            this.chunks = new byte[this.chunkCount][];
            this.createdNanos = now;
            this.lastProgressNanos = now;
        }

        private void validateMetadata(MessageChunkBuffer packet, ReceiveLimits limits) {
            if (packet.messageTypeId() != this.messageTypeId
                    || packet.chunkCount() != this.chunkCount
                    || packet.compressed() != this.compressed
                    || packet.uncompressedSize() != this.uncompressedSize
                    || packet.checksum() != this.checksum
                    || !limits.equals(this.limits)) {
                throw new IllegalArgumentException(
                        "Conflicting chunked-message transfer metadata"
                );
            }
        }

        private void accept(MessageChunkBuffer packet, long now) {
            byte[] data = packet.data();
            byte[] existing = this.chunks[packet.sequence()];
            if (existing != null) {
                if (!Arrays.equals(existing, data)) {
                    throw new IllegalArgumentException("Conflicting duplicate message chunk");
                }
                return;
            }
            if (this.retainedBytes + data.length > this.uncompressedSize) {
                throw new IllegalArgumentException(
                        "Chunked message exceeds retained-byte limit"
                );
            }
            this.chunks[packet.sequence()] = data;
            this.receivedChunks++;
            this.retainedBytes += data.length;
            this.lastProgressNanos = now;
        }

        private boolean complete() {
            return this.receivedChunks == this.chunkCount;
        }

        private FailureReason expirationReason(long now) {
            if (now - this.createdNanos >= MAX_LIFETIME_NANOS) {
                return FailureReason.LIFETIME_TIMEOUT;
            }
            if (now - this.lastProgressNanos >= IDLE_TIMEOUT_NANOS) {
                return FailureReason.IDLE_TIMEOUT;
            }
            return null;
        }

        private byte[] reassemble() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(this.retainedBytes);
            for (byte[] chunk : this.chunks) {
                output.write(chunk, 0, chunk.length);
            }
            byte[] transmitted = output.toByteArray();
            byte[] uncompressed;
            if (this.compressed) {
                uncompressed = decompress(transmitted, this.uncompressedSize);
            } else {
                if (transmitted.length != this.uncompressedSize) {
                    throw new IllegalArgumentException(
                            "Chunked message does not match declared size"
                    );
                }
                uncompressed = transmitted;
            }
            CRC32 crc32 = new CRC32();
            crc32.update(uncompressed);
            if ((int) crc32.getValue() != this.checksum) {
                throw new IllegalArgumentException("Chunked-message checksum mismatch");
            }
            return uncompressed;
        }
    }
}
