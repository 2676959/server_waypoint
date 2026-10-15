package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteCatalogSnapshot;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.*;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Blocking reusable TCP channel. Invoke from bounded transport workers, never a game thread.
 * One reader and one writer may run concurrently; each direction, state and crypto are serialized.
 * No callbacks or unbounded executor queues. Every protocol/I/O failure permanently closes the channel.
 */
public final class TcpChannel implements AutoCloseable {
    public record Received(ApplicationEnvelope envelope, RemoteCatalogSnapshot completedCatalog) { }
    private final Socket socket;
    private final TransportMode mode;
    private final RemoteServerId serverId;
    private final Set<Integer> capabilities;
    private final NoiseRecordCipher cipher;
    private final TcpLimits limits;
    private final ProtocolLimits protocol;
    private final ApplicationCodec codec;
    private final TcpSessionState inbound;
    private final TcpSessionState outbound;
    private final Object state = new Object();
    private final Object reader = new Object();
    private final Object writer = new Object();
    private final Runnable release;
    private final ScheduledFuture<?> expiry;
    private volatile boolean closed;
    private volatile ConnectionFailureReason closeReason = ConnectionFailureReason.CHANNEL_CLOSED;
    private long activity = System.nanoTime();

    TcpChannel(Socket socket, TransportMode mode, TcpWire.Hello hello, NoiseRecordCipher cipher,
               TcpLimits limits, ProtocolLimits protocol, Runnable release) {
        this.socket = socket;
        this.mode = mode;
        serverId = hello.serverId();
        capabilities = hello.capabilities();
        this.cipher = cipher;
        this.limits = limits;
        this.protocol = protocol;
        codec = new ApplicationCodec(protocol);
        inbound = new TcpSessionState(limits, protocol);
        outbound = new TcpSessionState(limits, protocol);
        this.release = release;
        int interval = Math.max(1, Math.min(1000, limits.operationMillis()));
        synchronized (state) {
            expiry = TcpWire.TIMER.scheduleWithFixedDelay(this::expire, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    public ProtocolLimits protocolLimits() { return protocol; }

    public TransportMode mode() { return mode; }
    public boolean authenticated() { return mode == TransportMode.NOISE_KK; }
    public RemoteServerId serverId() { return serverId; }
    public Set<Integer> capabilities() { return capabilities; }
    public boolean isClosed() { return closed || socket.isClosed(); }

    public void send(UUID requestId, ApplicationMessage message) throws IOException {
        synchronized (writer) {
            TcpWire.Deadline deadline = TcpWire.deadline(socket, limits.operationMillis(), this::timedOut);
            ConnectionFailureReason phase = ConnectionFailureReason.PROTOCOL_VIOLATION;
            try {
                byte[] frame;
                synchronized (state) {
                    requireOpen();
                    ApplicationEnvelope envelope = new ApplicationEnvelope(outbound.sequence, requestId, message);
                    frame = codec.encode(envelope);
                    outbound.accept(envelope, codec);
                    inbound.response(envelope);
                }
                int offset = 0;
                phase = ConnectionFailureReason.IO_ERROR;
                while (offset < frame.length) {
                    int count = Math.min(TcpWire.CLEAR - 8, frame.length - offset);
                    byte[] fragment = ByteBuffer.allocate(count + 8).putInt(frame.length).putInt(offset)
                            .put(frame, offset, count).array();
                    TcpWire.write(socket, cipher == null ? fragment : cipher.encrypt(fragment), TcpWire.RECORD);
                    offset += count;
                }
                synchronized (state) { requireOpen(); activity = System.nanoTime(); }
            } catch (Exception failure) {
                throw reject(failure, deadline, phase);
            } finally { deadline.close(); }
        }
    }

    public Received receive() throws IOException {
        synchronized (reader) {
            TcpWire.Deadline deadline = TcpWire.deadline(socket, limits.operationMillis(), this::timedOut);
            ConnectionFailureReason phase = ConnectionFailureReason.IO_ERROR;
            try {
                synchronized (state) { requireOpen(); }
                byte[] frame = null;
                int offset = 0;
                do {
                    byte[] record = TcpWire.read(socket, cipher == null ? TcpWire.CLEAR : TcpWire.RECORD);
                    byte[] clear = cipher == null ? record : cipher.decrypt(record);
                    if (clear.length <= 8) throw new ConnectionFailureException(ConnectionFailureReason.INVALID_FRAME);
                    ByteBuffer fragment = ByteBuffer.wrap(clear);
                    int total = fragment.getInt();
                    int position = fragment.getInt();
                    if (total < ApplicationCodec.HEADER_BYTES || total > protocol.frameBytes()
                            || position != offset || fragment.remaining() > total - offset) {
                        throw new ConnectionFailureException(ConnectionFailureReason.INVALID_FRAME);
                    }
                    if (frame == null) frame = new byte[total];
                    if (frame.length != total) throw new ConnectionFailureException(ConnectionFailureReason.INVALID_FRAME);
                    int count = fragment.remaining();
                    fragment.get(frame, offset, count);
                    offset += count;
                } while (offset < frame.length);
                phase = ConnectionFailureReason.PROTOCOL_VIOLATION;
                ApplicationEnvelope envelope = codec.decode(frame);
                synchronized (state) {
                    requireOpen();
                    RemoteCatalogSnapshot catalog = inbound.accept(envelope, codec);
                    outbound.response(envelope);
                    activity = System.nanoTime();
                    return new Received(envelope, catalog);
                }
            } catch (Exception failure) {
                throw reject(failure, deadline, phase);
            } finally { deadline.close(); }
        }
    }

    private void timedOut() {
        synchronized (state) {
            if (!closed && closeReason == ConnectionFailureReason.CHANNEL_CLOSED) {
                closeReason = ConnectionFailureReason.TIMEOUT;
            }
        }
    }

    private ConnectionFailureException reject(Exception failure, TcpWire.Deadline deadline,
                                               ConnectionFailureReason phase) {
        ConnectionFailureException diagnostic;
        synchronized (state) {
            if (closeReason != ConnectionFailureReason.CHANNEL_CLOSED) {
                diagnostic = new ConnectionFailureException(closeReason, failure);
            } else if (deadline.expired()) {
                diagnostic = new ConnectionFailureException(ConnectionFailureReason.TIMEOUT, failure);
            } else {
                diagnostic = ConnectionFailureException.wrap(phase, failure);
            }
            closeReason = diagnostic.reason();
        }
        close();
        return diagnostic;
    }

    private void requireOpen() throws IOException {
        if (isClosed()) throw new ConnectionFailureException(closeReason);
        long now = System.nanoTime();
        if (inbound.expired(now) || outbound.expired(now)) throw new ConnectionFailureException(ConnectionFailureReason.REQUEST_EXPIRED);
    }

    private void expire() {
        boolean expired;
        synchronized (state) {
            long now = System.nanoTime();
            if (!closed && !socket.isClosed() && closeReason == ConnectionFailureReason.CHANNEL_CLOSED) {
                if (inbound.expired(now) || outbound.expired(now)) closeReason = ConnectionFailureReason.REQUEST_EXPIRED;
                else if (now - activity >= limits.operationMillis() * 1_000_000L) closeReason = ConnectionFailureReason.TIMEOUT;
            }
            expired = socket.isClosed() || closeReason != ConnectionFailureReason.CHANNEL_CLOSED;
        }
        if (expired) close();
    }

    @Override public void close() {
        TcpWire.close(socket); // interrupts reads/writes without acquiring their monitors
        synchronized (state) {
            if (closed) return;
            closed = true;
            if (expiry != null) expiry.cancel(false);
            if (cipher != null) cipher.close();
            inbound.clear(); outbound.clear();
        }
        release.run();
    }
}
