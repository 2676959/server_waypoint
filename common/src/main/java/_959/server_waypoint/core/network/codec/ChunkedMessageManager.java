package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.ChunkedMessage;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
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
    private final long maxGlobalRetainedBytes;
    private final int maxFramesPerPeerTick;
    private final int maxBytesPerPeerTick;
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
        if (maxGlobalRetainedBytes <= 0
                || maxFramesPerPeerTick <= 0
                || maxBytesPerPeerTick <= 0) {
            throw new IllegalArgumentException("Chunked-message limits must be positive");
        }
        this.maxGlobalRetainedBytes = maxGlobalRetainedBytes;
        this.maxFramesPerPeerTick = maxFramesPerPeerTick;
        this.maxBytesPerPeerTick = maxBytesPerPeerTick;
    }

    public ChunkedMessageSendResult send(
            P peer,
            ChunkedMessage message,
            boolean compressionEnabled,
            Consumer<List<MessageChunkBuffer>> batchSender
    ) {
        return this.send(peer, prepare(message, compressionEnabled), batchSender);
    }

    public ChunkedMessageSendResult send(
            P peer,
            PreparedMessage message,
            Consumer<List<MessageChunkBuffer>> batchSender
    ) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(batchSender, "batchSender");
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
                    return ChunkedMessageSendResult.PEER_BUSY;
                }
                if (!this.retainOutgoingBody(message)) {
                    return ChunkedMessageSendResult.PEER_BUSY;
                }
                state.outgoing.addLast(new OutgoingTransfer(message, batchSender));
                state.outgoingRetainedBytes += message.retainedBytes;
                break;
            } finally {
                state.stateLock.unlock();
            }
        }
        return this.drainPeer(peer, state) == DrainResult.FAILED
                ? ChunkedMessageSendResult.DELIVERY_FAILED
                : ChunkedMessageSendResult.QUEUED;
    }

    public List<ChunkedMessage> receive(
            P peer,
            MessageChunkBuffer packet
    ) {
        return this.receive(peer, packet, DEFAULT_RECEIVE_LIMITS);
    }

    public List<ChunkedMessage> receive(
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
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(limits, "limits");
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
                        return List.of();
                    }
                    state.incoming.remove(packet.transferId());
                    state.incomingDeclaredBytes -= transfer.uncompressedSize;
                    completed = transfer;
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
            try {
                return List.of(decodeMessage(completed.messageTypeId, bytes, completed.limits));
            } catch (RuntimeException exception) {
                throw new ReceiveException(
                        completed.messageTypeId,
                        FailureReason.DECODE_FAILED,
                        completed.transferId,
                        exception
                );
            }
        } finally {
            state.decodeLock.unlock();
            this.releaseIncomingBytes(completed.reservedBytes);
        }
    }

    public List<ReceiveFailure<P>> tick() {
        return this.tick(System.nanoTime());
    }

    public List<ReceiveFailure<P>> tick(P peer) {
        PeerState state = this.peers.get(peer);
        if (state == null) {
            return List.of();
        }
        long now = System.nanoTime();
        List<ReceiveFailure<P>> failures = this.expireIncoming(peer, state, now);
        this.drainPeer(peer, state);
        return failures;
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

    List<ReceiveFailure<P>> tick(long now) {
        List<ReceiveFailure<P>> failures = new ArrayList<>();
        for (Map.Entry<P, PeerState> entry : this.peers.entrySet()) {
            failures.addAll(this.expireIncoming(entry.getKey(), entry.getValue(), now));
            this.drainPeer(entry.getKey(), entry.getValue());
        }
        return List.copyOf(failures);
    }

    public void clear(P peer) {
        PeerState state = this.peers.remove(peer);
        if (state == null) {
            return;
        }
        List<PreparedMessage> outgoingBodies = new ArrayList<>();
        long incomingBytes = 0;
        state.stateLock.lock();
        try {
            state.closed = true;
            for (OutgoingTransfer transfer : state.outgoing) {
                outgoingBodies.add(transfer.message);
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
        outgoingBodies.forEach(this::releaseOutgoingBody);
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

    private DrainResult drainPeer(P peer, PeerState state) {
        PendingBatch pending;
        state.stateLock.lock();
        try {
            if (state.closed || state.inFlight != null) {
                return DrainResult.NONE;
            }
            OutgoingTransfer transfer = state.outgoing.peekFirst();
            if (transfer == null) {
                return DrainResult.NONE;
            }
            int start = transfer.nextFrame;
            int end = start;
            int bytes = 0;
            List<MessageChunkBuffer> frames = transfer.message.frames;
            while (end < frames.size() && end - start < this.maxFramesPerPeerTick) {
                int frameBytes = frames.get(end).dataLength();
                if (end > start && bytes + frameBytes > this.maxBytesPerPeerTick) {
                    break;
                }
                bytes += frameBytes;
                end++;
            }
            pending = new PendingBatch(
                    transfer,
                    end,
                    List.copyOf(frames.subList(start, end))
            );
            state.inFlight = pending;
        } finally {
            state.stateLock.unlock();
        }

        try {
            pending.transfer.batchSender.accept(pending.frames);
        } catch (RuntimeException exception) {
            this.clear(peer);
            return DrainResult.FAILED;
        }

        PreparedMessage completedBody = null;
        state.stateLock.lock();
        try {
            if (state.closed || state.inFlight != pending) {
                return DrainResult.SENT;
            }
            pending.transfer.nextFrame = pending.nextFrame;
            if (pending.transfer.nextFrame == pending.transfer.message.frames.size()) {
                OutgoingTransfer removed = state.outgoing.removeFirst();
                if (removed != pending.transfer) {
                    throw new IllegalStateException("Chunked-message outbound queue changed unexpectedly");
                }
                state.outgoingRetainedBytes -= removed.message.retainedBytes;
                completedBody = removed.message;
            }
            state.inFlight = null;
        } finally {
            state.stateLock.unlock();
        }
        if (completedBody != null) {
            this.releaseOutgoingBody(completedBody);
        }
        return DrainResult.SENT;
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

    private static long incomingReservation(int uncompressedSize) {
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

    private enum DrainResult {
        NONE,
        SENT,
        FAILED
    }

    private static final class PeerState {
        private final ReentrantLock stateLock = new ReentrantLock();
        private final ReentrantLock decodeLock = new ReentrantLock();
        private final ArrayDeque<OutgoingTransfer> outgoing = new ArrayDeque<>();
        private final Map<UUID, IncomingTransfer> incoming = new java.util.HashMap<>();
        private long outgoingRetainedBytes;
        private long incomingDeclaredBytes;
        private PendingBatch inFlight;
        private boolean closed;
    }

    private static final class OutgoingTransfer {
        private final PreparedMessage message;
        private final Consumer<List<MessageChunkBuffer>> batchSender;
        private int nextFrame;

        private OutgoingTransfer(
                PreparedMessage message,
                Consumer<List<MessageChunkBuffer>> batchSender
        ) {
            this.message = message;
            this.batchSender = batchSender;
        }
    }

    private record PendingBatch(
            OutgoingTransfer transfer,
            int nextFrame,
            List<MessageChunkBuffer> frames
    ) {
    }

    private record FailureKey(int messageTypeId, FailureReason reason) {
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
