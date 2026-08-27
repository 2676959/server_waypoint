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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Encodes, chunks, reassembles, retries, and sequence-orders logical messages.
 * Public operations are synchronized because loader network and tick threads may
 * share one manager.
 */
public final class ChunkedMessageManager<P> {
    public static final int MAX_CHUNK_DATA_SIZE = 24 * 1_024;
    public static final int MAX_MESSAGE_BYTES = 64 * 1_024 * 1_024;
    public static final int MAX_CHUNKS_PER_TRANSFER =
            (MAX_MESSAGE_BYTES + MAX_CHUNK_DATA_SIZE - 1) / MAX_CHUNK_DATA_SIZE;
    public static final int MAX_ACTIVE_TRANSFERS_PER_PEER = 8;
    public static final int MAX_DECODED_OBJECTS = 1_000_000;
    private static final int MAX_COMPLETED_TRANSFERS_PER_PEER = 64;
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long TRANSFER_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int MAX_RETRY_REQUESTS = 3;

    private final Map<TransferKey<P>, OutgoingTransfer> outgoing = new HashMap<>();
    private final Map<TransferKey<P>, IncomingTransfer> incoming = new HashMap<>();
    private final Map<TransferKey<P>, CompletedTransfer> completedTransfers = new HashMap<>();
    private final Map<SequenceKey<P>, UUID> activeIncomingSequences = new HashMap<>();
    private final Map<SequenceKey<P>, RecentSequence> recentSequences = new HashMap<>();
    private final Map<P, Long> nextOutgoingSequences = new HashMap<>();
    private final Map<P, Long> nextIncomingSequences = new HashMap<>();
    private final Map<P, TreeMap<Long, CompletedMessage>> orderedMessages = new HashMap<>();
    private final Map<P, OrderingGap> orderingGaps = new HashMap<>();

    public synchronized ChunkedMessageSendResult send(
            P peer,
            ChunkedMessage message,
            boolean compressionEnabled,
            Consumer<MessageChunkBuffer> packetSender
    ) {
        long now = System.nanoTime();
        this.cleanupExpiredCaches(now);
        if (this.countTransfers(this.outgoing, peer) >= MAX_ACTIVE_TRANSFERS_PER_PEER) {
            return ChunkedMessageSendResult.PEER_BUSY;
        }
        long logicalSequence = this.nextOutgoingSequences.getOrDefault(peer, 0L);
        List<MessageChunkBuffer> packets = createTransfer(message, compressionEnabled, logicalSequence);
        int retainedBytes = packets.stream().mapToInt(MessageChunkBuffer::dataLength).sum();
        if (this.retainedOutgoingBytes(peer) + retainedBytes > MAX_MESSAGE_BYTES) {
            return ChunkedMessageSendResult.PEER_BUSY;
        }
        this.nextOutgoingSequences.put(peer, incrementSequence(logicalSequence));
        UUID transferId = packets.get(0).transferId();
        this.outgoing.put(
                new TransferKey<>(peer, transferId),
                new OutgoingTransfer(packets, packetSender, retainedBytes, now)
        );
        for (MessageChunkBuffer packet : packets) {
            packetSender.accept(packet);
        }
        return ChunkedMessageSendResult.QUEUED;
    }

    public synchronized List<ChunkedMessage> receive(
            P peer,
            MessageChunkBuffer packet,
            Consumer<MessageChunkBuffer> responseSender,
            Runnable orderingFailureHandler
    ) {
        long now = System.nanoTime();
        this.cleanupExpiredCaches(now);
        TransferKey<P> key = new TransferKey<>(peer, packet.transferId());
        return switch (packet.operation()) {
            case ACKNOWLEDGEMENT -> {
                this.outgoing.remove(key);
                yield List.of();
            }
            case RETRY -> {
                this.resendMissing(key, packet.missingSequences(), responseSender, now);
                yield List.of();
            }
            case CHUNK -> this.receiveChunk(
                    key,
                    packet,
                    responseSender,
                    orderingFailureHandler,
                    now
            );
        };
    }

    public synchronized void tick() {
        this.tick(System.nanoTime());
    }

    synchronized void tick(long now) {
        List<Runnable> actions = new ArrayList<>();
        Iterator<Map.Entry<TransferKey<P>, OutgoingTransfer>> outgoingIterator =
                this.outgoing.entrySet().iterator();
        while (outgoingIterator.hasNext()) {
            OutgoingTransfer transfer = outgoingIterator.next().getValue();
            if (now - transfer.createdNanos >= TRANSFER_TIMEOUT_NANOS) {
                outgoingIterator.remove();
                continue;
            }
            if (now - transfer.lastActivityNanos < RETRY_DELAY_NANOS) {
                continue;
            }
            if (transfer.retryAttempts >= MAX_RETRY_REQUESTS) {
                outgoingIterator.remove();
                continue;
            }
            MessageChunkBuffer probe = transfer.packets.get(0);
            actions.add(() -> transfer.packetSender.accept(probe));
            transfer.lastActivityNanos = now;
            transfer.retryAttempts++;
        }

        Iterator<Map.Entry<TransferKey<P>, IncomingTransfer>> incomingIterator =
                this.incoming.entrySet().iterator();
        Map<P, Runnable> failedPeers = new HashMap<>();
        while (incomingIterator.hasNext()) {
            Map.Entry<TransferKey<P>, IncomingTransfer> entry = incomingIterator.next();
            IncomingTransfer transfer = entry.getValue();
            if (now - transfer.createdNanos >= TRANSFER_TIMEOUT_NANOS) {
                incomingIterator.remove();
                this.activeIncomingSequences.remove(
                        new SequenceKey<>(entry.getKey().peer, transfer.logicalSequence)
                );
                failedPeers.put(entry.getKey().peer, transfer.orderingFailureHandler);
                continue;
            }
            if (now - transfer.lastProgressNanos < RETRY_DELAY_NANOS
                    || now - transfer.lastRetryNanos < RETRY_DELAY_NANOS) {
                continue;
            }
            if (transfer.retryRequests >= MAX_RETRY_REQUESTS) {
                continue;
            }
            MessageChunkBuffer retry = MessageChunkBuffer.retry(
                    entry.getKey().transferId,
                    transfer.missingSequences()
            );
            actions.add(() -> transfer.responseSender.accept(retry));
            transfer.lastRetryNanos = now;
            transfer.retryRequests++;
        }

        for (Map.Entry<P, OrderingGap> entry : this.orderingGaps.entrySet()) {
            if (now - entry.getValue().createdNanos >= TRANSFER_TIMEOUT_NANOS) {
                failedPeers.put(entry.getKey(), entry.getValue().failureHandler);
            }
        }
        for (Map.Entry<P, Runnable> entry : failedPeers.entrySet()) {
            this.cancelOrderedQueue(entry.getKey());
            actions.add(entry.getValue());
        }
        this.cleanupExpiredCaches(now);
        actions.forEach(Runnable::run);
    }

    public synchronized void clear(P peer) {
        this.outgoing.keySet().removeIf(key -> java.util.Objects.equals(key.peer, peer));
        this.incoming.keySet().removeIf(key -> java.util.Objects.equals(key.peer, peer));
        this.completedTransfers.keySet().removeIf(key -> java.util.Objects.equals(key.peer, peer));
        this.activeIncomingSequences.keySet().removeIf(key -> java.util.Objects.equals(key.peer, peer));
        this.recentSequences.keySet().removeIf(key -> java.util.Objects.equals(key.peer, peer));
        this.nextOutgoingSequences.remove(peer);
        this.nextIncomingSequences.remove(peer);
        this.orderedMessages.remove(peer);
        this.orderingGaps.remove(peer);
    }

    public synchronized void clearAll() {
        this.outgoing.clear();
        this.incoming.clear();
        this.completedTransfers.clear();
        this.activeIncomingSequences.clear();
        this.recentSequences.clear();
        this.nextOutgoingSequences.clear();
        this.nextIncomingSequences.clear();
        this.orderedMessages.clear();
        this.orderingGaps.clear();
    }

    public static List<MessageChunkBuffer> createTransfer(
            ChunkedMessage message,
            boolean compressionEnabled,
            long logicalSequence
    ) {
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
        List<MessageChunkBuffer> result = new ArrayList<>(chunkCount);
        for (int sequence = 0; sequence < chunkCount; sequence++) {
            int from = sequence * MAX_CHUNK_DATA_SIZE;
            int to = Math.min(from + MAX_CHUNK_DATA_SIZE, transmitted.length);
            result.add(MessageChunkBuffer.chunk(
                    transferId,
                    logicalSequence,
                    message.getType().id(),
                    sequence,
                    chunkCount,
                    useCompression,
                    uncompressed.length,
                    checksum,
                    Arrays.copyOfRange(transmitted, from, to)
            ));
        }
        return List.copyOf(result);
    }

    public static void validateEncodable(ChunkedMessage message) {
        encodeMessage(message);
    }

    private List<ChunkedMessage> receiveChunk(
            TransferKey<P> key,
            MessageChunkBuffer packet,
            Consumer<MessageChunkBuffer> responseSender,
            Runnable orderingFailureHandler,
            long now
    ) {
        CompletedTransfer completed = this.completedTransfers.get(key);
        if (completed != null && completed.expiresAt > now) {
            if (!completed.matches(packet)) {
                throw new IllegalArgumentException("Conflicting completed message chunk");
            }
            responseSender.accept(MessageChunkBuffer.acknowledgement(packet.transferId()));
            return List.of();
        }
        validateChunk(packet);
        SequenceKey<P> sequenceKey = new SequenceKey<>(key.peer, packet.logicalSequence());
        TreeMap<Long, CompletedMessage> completedQueue = this.orderedMessages.get(key.peer);
        CompletedMessage queued = completedQueue == null
                ? null
                : completedQueue.get(packet.logicalSequence());
        if (queued != null) {
            if (!queued.matches(packet)) {
                throw new IllegalArgumentException("Conflicting queued logical message sequence");
            }
            responseSender.accept(MessageChunkBuffer.acknowledgement(packet.transferId()));
            return List.of();
        }
        RecentSequence recent = this.recentSequences.get(sequenceKey);
        if (recent != null) {
            if (!recent.transferId.equals(packet.transferId())) {
                throw new IllegalArgumentException("Conflicting duplicate logical message sequence");
            }
            responseSender.accept(MessageChunkBuffer.acknowledgement(packet.transferId()));
            return List.of();
        }
        UUID activeTransferId = this.activeIncomingSequences.get(sequenceKey);
        if (activeTransferId != null && !activeTransferId.equals(packet.transferId())) {
            throw new IllegalArgumentException("Conflicting active logical message sequence");
        }
        long expectedSequence = this.nextIncomingSequences.getOrDefault(key.peer, 0L);
        if (packet.logicalSequence() < expectedSequence) {
            throw new IllegalArgumentException("Stale logical message sequence");
        }

        IncomingTransfer transfer = this.incoming.get(key);
        if (transfer == null) {
            if (this.countTransfers(this.incoming, key.peer) >= MAX_ACTIVE_TRANSFERS_PER_PEER) {
                throw new IllegalStateException("Too many incoming chunked-message transfers for peer");
            }
            if (this.declaredIncomingBytes(key.peer) + packet.uncompressedSize() > MAX_MESSAGE_BYTES) {
                throw new IllegalStateException("Too many incoming chunked-message bytes retained for peer");
            }
            transfer = new IncomingTransfer(
                    packet,
                    responseSender,
                    orderingFailureHandler,
                    now
            );
            this.incoming.put(key, transfer);
            this.activeIncomingSequences.put(sequenceKey, packet.transferId());
        } else {
            transfer.validateMetadata(packet);
            transfer.responseSender = responseSender;
            transfer.orderingFailureHandler = orderingFailureHandler;
        }
        transfer.accept(packet, now);
        if (!transfer.complete()) {
            if (packet.sequence() == packet.chunkCount() - 1
                    && transfer.retryRequests < MAX_RETRY_REQUESTS) {
                responseSender.accept(MessageChunkBuffer.retry(
                        packet.transferId(),
                        transfer.missingSequences()
                ));
                transfer.lastRetryNanos = now;
                transfer.retryRequests++;
            }
            this.noteOrderingGap(key.peer, packet.logicalSequence(), orderingFailureHandler, now);
            return List.of();
        }

        this.incoming.remove(key);
        this.activeIncomingSequences.remove(sequenceKey);
        ChunkedMessage message = transfer.decode();
        this.rememberCompleted(key, sequenceKey, transfer, now + TRANSFER_TIMEOUT_NANOS);
        responseSender.accept(MessageChunkBuffer.acknowledgement(packet.transferId()));

        TreeMap<Long, CompletedMessage> queue =
                this.orderedMessages.computeIfAbsent(key.peer, ignored -> new TreeMap<>());
        CompletedMessage previous = queue.putIfAbsent(
                packet.logicalSequence(),
                new CompletedMessage(
                        packet.transferId(),
                        message,
                        transfer.uncompressedSize,
                        transfer.messageTypeId,
                        transfer.chunkCount,
                        transfer.compressed,
                        transfer.checksum
                )
        );
        if (previous != null && !previous.transferId.equals(packet.transferId())) {
            throw new IllegalArgumentException("Conflicting completed logical message sequence");
        }
        if (queue.size() > MAX_COMPLETED_TRANSFERS_PER_PEER
                || this.orderedRetainedBytes(key.peer) > MAX_MESSAGE_BYTES) {
            this.cancelOrderedQueue(key.peer);
            orderingFailureHandler.run();
            return List.of();
        }
        return this.drainOrderedMessages(key.peer, orderingFailureHandler, now);
    }

    private List<ChunkedMessage> drainOrderedMessages(
            P peer,
            Runnable orderingFailureHandler,
            long now
    ) {
        TreeMap<Long, CompletedMessage> queue = this.orderedMessages.get(peer);
        if (queue == null) {
            return List.of();
        }
        long expected = this.nextIncomingSequences.getOrDefault(peer, 0L);
        List<ChunkedMessage> result = new ArrayList<>();
        CompletedMessage completed;
        while ((completed = queue.remove(expected)) != null) {
            result.add(completed.message);
            expected = incrementSequence(expected);
        }
        this.nextIncomingSequences.put(peer, expected);
        if (queue.isEmpty()) {
            this.orderedMessages.remove(peer);
            this.orderingGaps.remove(peer);
        } else {
            this.noteOrderingGap(peer, queue.firstKey(), orderingFailureHandler, now);
        }
        return List.copyOf(result);
    }

    private void noteOrderingGap(P peer, long observedSequence, Runnable failureHandler, long now) {
        long expected = this.nextIncomingSequences.getOrDefault(peer, 0L);
        if (observedSequence > expected) {
            this.orderingGaps.putIfAbsent(peer, new OrderingGap(now, failureHandler));
        }
    }

    private void cancelOrderedQueue(P peer) {
        long highest = this.nextIncomingSequences.getOrDefault(peer, 0L) - 1;
        TreeMap<Long, CompletedMessage> queue = this.orderedMessages.remove(peer);
        if (queue != null && !queue.isEmpty()) {
            highest = Math.max(highest, queue.lastKey());
        }
        Iterator<Map.Entry<TransferKey<P>, IncomingTransfer>> iterator = this.incoming.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TransferKey<P>, IncomingTransfer> entry = iterator.next();
            if (java.util.Objects.equals(entry.getKey().peer, peer)) {
                highest = Math.max(highest, entry.getValue().logicalSequence);
                this.activeIncomingSequences.remove(
                        new SequenceKey<>(peer, entry.getValue().logicalSequence)
                );
                iterator.remove();
            }
        }
        this.nextIncomingSequences.put(peer, incrementSequence(highest));
        this.orderingGaps.remove(peer);
    }

    private void resendMissing(
            TransferKey<P> key,
            int[] missingSequences,
            Consumer<MessageChunkBuffer> packetSender,
            long now
    ) {
        OutgoingTransfer transfer = this.outgoing.get(key);
        if (transfer == null) {
            return;
        }
        transfer.lastActivityNanos = now;
        boolean[] sent = new boolean[transfer.packets.size()];
        for (int missingSequence : missingSequences) {
            if (missingSequence < 0 || missingSequence >= transfer.packets.size()) {
                throw new IllegalArgumentException("Invalid missing message-chunk sequence: " + missingSequence);
            }
            if (!sent[missingSequence]) {
                packetSender.accept(transfer.packets.get(missingSequence));
                sent[missingSequence] = true;
            }
        }
    }

    private void cleanupExpiredCaches(long now) {
        this.outgoing.entrySet().removeIf(entry ->
                now - entry.getValue().createdNanos >= TRANSFER_TIMEOUT_NANOS);
        this.completedTransfers.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        this.recentSequences.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private <T> int countTransfers(Map<TransferKey<P>, T> transfers, P peer) {
        int count = 0;
        for (TransferKey<P> key : transfers.keySet()) {
            if (java.util.Objects.equals(key.peer, peer)) {
                count++;
            }
        }
        return count;
    }

    private long declaredIncomingBytes(P peer) {
        long bytes = 0;
        for (Map.Entry<TransferKey<P>, IncomingTransfer> entry : this.incoming.entrySet()) {
            if (java.util.Objects.equals(entry.getKey().peer, peer)) {
                bytes += entry.getValue().uncompressedSize;
            }
        }
        return bytes;
    }

    private long retainedOutgoingBytes(P peer) {
        long bytes = 0;
        for (Map.Entry<TransferKey<P>, OutgoingTransfer> entry : this.outgoing.entrySet()) {
            if (java.util.Objects.equals(entry.getKey().peer, peer)) {
                bytes += entry.getValue().retainedBytes;
            }
        }
        return bytes;
    }

    private long orderedRetainedBytes(P peer) {
        TreeMap<Long, CompletedMessage> queue = this.orderedMessages.get(peer);
        if (queue == null) {
            return 0;
        }
        long bytes = 0;
        for (CompletedMessage message : queue.values()) {
            bytes += message.retainedBytes;
        }
        return bytes;
    }

    private void rememberCompleted(
            TransferKey<P> transferKey,
            SequenceKey<P> sequenceKey,
            IncomingTransfer transfer,
            long expiresAt
    ) {
        if (this.countTransfers(this.completedTransfers, transferKey.peer)
                >= MAX_COMPLETED_TRANSFERS_PER_PEER) {
            TransferKey<P> oldestKey = null;
            long oldestExpiry = Long.MAX_VALUE;
            for (Map.Entry<TransferKey<P>, CompletedTransfer> entry
                    : this.completedTransfers.entrySet()) {
                if (java.util.Objects.equals(entry.getKey().peer, transferKey.peer)
                        && entry.getValue().expiresAt < oldestExpiry) {
                    oldestKey = entry.getKey();
                    oldestExpiry = entry.getValue().expiresAt;
                }
            }
            if (oldestKey != null) {
                this.completedTransfers.remove(oldestKey);
                UUID removedTransferId = oldestKey.transferId;
                this.recentSequences.entrySet().removeIf(entry ->
                        java.util.Objects.equals(entry.getKey().peer, transferKey.peer)
                                && entry.getValue().transferId.equals(removedTransferId)
                );
            }
        }
        this.completedTransfers.put(
                transferKey,
                CompletedTransfer.from(transfer, expiresAt)
        );
        this.recentSequences.put(
                sequenceKey,
                new RecentSequence(transferKey.transferId, expiresAt)
        );
    }

    private static void validateChunk(MessageChunkBuffer packet) {
        if (packet.logicalSequence() < 0) {
            throw new IllegalArgumentException("Invalid logical message sequence");
        }
        ChunkedMessageRegistry.get(packet.messageTypeId());
        if (packet.chunkCount() <= 0 || packet.chunkCount() > MAX_CHUNKS_PER_TRANSFER) {
            throw new IllegalArgumentException("Invalid message chunk count: " + packet.chunkCount());
        }
        if (packet.sequence() < 0 || packet.sequence() >= packet.chunkCount()) {
            throw new IllegalArgumentException("Invalid message chunk sequence: " + packet.sequence());
        }
        if (packet.uncompressedSize() < 0 || packet.uncompressedSize() > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid chunked-message size: " + packet.uncompressedSize());
        }
        int dataLength = packet.dataLength();
        if (dataLength > MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid message chunk length: " + dataLength);
        }
        if (packet.uncompressedSize() > 0 && dataLength == 0) {
            throw new IllegalArgumentException("Non-empty logical message has an empty chunk");
        }
        if (packet.sequence() < packet.chunkCount() - 1 && dataLength != MAX_CHUNK_DATA_SIZE) {
            throw new IllegalArgumentException("Non-final message chunk has invalid length");
        }
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

    private static ChunkedMessage decodeMessage(int typeId, byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            ChunkedMessage result = ChunkedMessageRegistry.decode(
                    typeId,
                    buffer,
                    new DecodingContext(MAX_MESSAGE_BYTES, MAX_DECODED_OBJECTS)
            );
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Chunked message has trailing bytes");
            }
            return result;
        } catch (IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("Chunked message ended before decoding completed", exception);
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

    private static long incrementSequence(long sequence) {
        if (sequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Logical message sequence exhausted");
        }
        return sequence + 1;
    }

    private record TransferKey<P>(P peer, UUID transferId) {
    }

    private record SequenceKey<P>(P peer, long logicalSequence) {
    }

    private record RecentSequence(UUID transferId, long expiresAt) {
    }

    private record CompletedTransfer(
            long logicalSequence,
            int messageTypeId,
            int chunkCount,
            boolean compressed,
            int uncompressedSize,
            int checksum,
            int[] chunkHashes,
            long expiresAt
    ) {
        private static CompletedTransfer from(IncomingTransfer transfer, long expiresAt) {
            int[] chunkHashes = new int[transfer.chunks.length];
            for (int sequence = 0; sequence < transfer.chunks.length; sequence++) {
                chunkHashes[sequence] = Arrays.hashCode(transfer.chunks[sequence]);
            }
            return new CompletedTransfer(
                    transfer.logicalSequence,
                    transfer.messageTypeId,
                    transfer.chunkCount,
                    transfer.compressed,
                    transfer.uncompressedSize,
                    transfer.checksum,
                    chunkHashes,
                    expiresAt
            );
        }

        private boolean matches(MessageChunkBuffer packet) {
            return this.logicalSequence == packet.logicalSequence()
                    && this.messageTypeId == packet.messageTypeId()
                    && this.chunkCount == packet.chunkCount()
                    && this.compressed == packet.compressed()
                    && this.uncompressedSize == packet.uncompressedSize()
                    && this.checksum == packet.checksum()
                    && packet.sequence() >= 0
                    && packet.sequence() < this.chunkHashes.length
                    && this.chunkHashes[packet.sequence()] == Arrays.hashCode(packet.data());
        }
    }

    private record CompletedMessage(
            UUID transferId,
            ChunkedMessage message,
            int retainedBytes,
            int messageTypeId,
            int chunkCount,
            boolean compressed,
            int checksum
    ) {
        private boolean matches(MessageChunkBuffer packet) {
            return this.transferId.equals(packet.transferId())
                    && this.messageTypeId == packet.messageTypeId()
                    && this.chunkCount == packet.chunkCount()
                    && this.compressed == packet.compressed()
                    && this.retainedBytes == packet.uncompressedSize()
                    && this.checksum == packet.checksum();
        }
    }

    private record OrderingGap(long createdNanos, Runnable failureHandler) {
    }

    private static final class OutgoingTransfer {
        private final List<MessageChunkBuffer> packets;
        private final Consumer<MessageChunkBuffer> packetSender;
        private final int retainedBytes;
        private final long createdNanos;
        private long lastActivityNanos;
        private int retryAttempts;

        private OutgoingTransfer(
                List<MessageChunkBuffer> packets,
                Consumer<MessageChunkBuffer> packetSender,
                int retainedBytes,
                long now
        ) {
            this.packets = packets;
            this.packetSender = packetSender;
            this.retainedBytes = retainedBytes;
            this.createdNanos = now;
            this.lastActivityNanos = now;
        }
    }

    private static final class IncomingTransfer {
        private final long logicalSequence;
        private final int messageTypeId;
        private final int chunkCount;
        private final boolean compressed;
        private final int uncompressedSize;
        private final int checksum;
        private final byte[][] chunks;
        private final long createdNanos;
        private int receivedChunks;
        private int retainedBytes;
        private long lastProgressNanos;
        private long lastRetryNanos;
        private int retryRequests;
        private Consumer<MessageChunkBuffer> responseSender;
        private Runnable orderingFailureHandler;

        private IncomingTransfer(
                MessageChunkBuffer first,
                Consumer<MessageChunkBuffer> responseSender,
                Runnable orderingFailureHandler,
                long now
        ) {
            this.logicalSequence = first.logicalSequence();
            this.messageTypeId = first.messageTypeId();
            this.chunkCount = first.chunkCount();
            this.compressed = first.compressed();
            this.uncompressedSize = first.uncompressedSize();
            this.checksum = first.checksum();
            this.chunks = new byte[this.chunkCount][];
            this.createdNanos = now;
            this.responseSender = responseSender;
            this.orderingFailureHandler = orderingFailureHandler;
            this.lastProgressNanos = now;
            this.lastRetryNanos = now;
        }

        private void validateMetadata(MessageChunkBuffer packet) {
            if (packet.logicalSequence() != this.logicalSequence
                    || packet.messageTypeId() != this.messageTypeId
                    || packet.chunkCount() != this.chunkCount
                    || packet.compressed() != this.compressed
                    || packet.uncompressedSize() != this.uncompressedSize
                    || packet.checksum() != this.checksum) {
                throw new IllegalArgumentException("Conflicting chunked-message transfer metadata");
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
            if (this.retainedBytes + data.length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Chunked message exceeds retained-byte limit");
            }
            this.chunks[packet.sequence()] = data;
            this.receivedChunks++;
            this.retainedBytes += data.length;
            this.lastProgressNanos = now;
        }

        private boolean complete() {
            return this.receivedChunks == this.chunkCount;
        }

        private int[] missingSequences() {
            int[] missing = new int[this.chunkCount - this.receivedChunks];
            int index = 0;
            for (int sequence = 0; sequence < this.chunks.length; sequence++) {
                if (this.chunks[sequence] == null) {
                    missing[index++] = sequence;
                }
            }
            return missing;
        }

        private ChunkedMessage decode() {
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
                    throw new IllegalArgumentException("Chunked message does not match declared size");
                }
                uncompressed = transmitted;
            }
            CRC32 crc32 = new CRC32();
            crc32.update(uncompressed);
            if ((int) crc32.getValue() != this.checksum) {
                throw new IllegalArgumentException("Chunked-message checksum mismatch");
            }
            return decodeMessage(this.messageTypeId, uncompressed);
        }
    }
}
