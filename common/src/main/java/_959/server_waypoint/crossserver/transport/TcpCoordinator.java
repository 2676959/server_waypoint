package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * Bounded blocking listener for use by a bounded worker owner. No accept loop or game lifecycle is
 * started here. Pins/admitted IDs are an immutable configuration snapshot; credential policy is step 7.
 */
public final class TcpCoordinator implements AutoCloseable {
    private final ServerSocket listener = new ServerSocket();
    private final TransportMode mode;
    private final NoiseKeys keys;
    private final Map<RemoteServerId, byte[]> pins;
    private final TcpLimits limits;
    private final ProtocolLimits protocol;
    private final Map<Socket, TcpChannel> sockets = new HashMap<>();
    private final Map<RemoteServerId, Socket> identities = new HashMap<>();
    private boolean closed;

    /** Plaintext registry values must be empty arrays; encrypted registry values are raw 32-byte pins. */
    public TcpCoordinator(TcpEndpoint endpoint, TransportMode mode, NoiseKeys keys,
                          Map<RemoteServerId, byte[]> admitted, TcpLimits limits, ProtocolLimits protocol) throws IOException {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.keys = keys;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        pins = new HashMap<>();
        try {
            if ((mode == TransportMode.NOISE_KK) != (keys != null) || admitted.size() > 4096) {
                throw new IllegalArgumentException("Invalid admission configuration");
            }
            for (var entry : admitted.entrySet()) {
                if (entry.getValue().length != (mode == TransportMode.NOISE_KK ? 32 : 0)) {
                    throw new IllegalArgumentException("Invalid admission pin");
                }
                pins.put(Objects.requireNonNull(entry.getKey()), entry.getValue().clone());
            }
            listener.bind(endpoint.resolve(mode), limits.connections());
        } catch (Exception failure) {
            listener.close();
            throw failure;
        }
    }

    public int port() { return listener.getLocalPort(); }
    public synchronized int connectionCount() { return sockets.size(); }

    /** May be called by multiple bounded workers. Failed handshakes never return a channel. */
    public TcpChannel accept() throws IOException {
        Socket socket = listener.accept();
        synchronized (this) {
            if (closed || sockets.size() >= limits.connections()) {
                TcpWire.close(socket);
                throw new IOException("Connection limit or closed listener");
            }
            sockets.put(socket, null); // count pre-authentication sockets too
        }
        ScheduledFuture<?> deadline = TcpWire.deadline(socket, limits.handshakeMillis());
        NoiseRecordCipher cipher = null;
        try {
            socket.setTcpNoDelay(true);
            if (mode == TransportMode.PLAINTEXT && (!socket.getInetAddress().isLoopbackAddress()
                    || !socket.getLocalAddress().isLoopbackAddress())) throw new IOException("Non-loopback socket");
            byte[] first = TcpWire.read(socket, 65_536);
            TcpWire.Hello hello = TcpWire.parse(first, mode, false);
            byte[] pin;
            synchronized (this) {
                pin = pins.get(hello.serverId());
                if (closed || pin == null || identities.containsKey(hello.serverId())) throw new IOException("Identity not admitted");
                identities.put(hello.serverId(), socket); // unauthenticated reservation, never published
            }
            byte[] second = TcpWire.hello(mode, hello.serverId(), hello.capabilities(), true);
            TcpWire.write(socket, second, 65_536);
            if (mode == TransportMode.NOISE_KK) {
                cipher = NoiseRecordCipher.handshake(socket, false, keys, pin, TcpWire.prologue(first, second));
            }
            synchronized (this) {
                if (closed || socket.isClosed()) throw new IOException("Listener closed or handshake expired");
                TcpChannel channel = new TcpChannel(socket, mode, hello, cipher, limits, protocol, () -> release(socket));
                sockets.put(socket, channel);
                return channel;
            }
        } catch (Exception failure) {
            TcpWire.close(socket);
            if (cipher != null) cipher.close();
            release(socket);
            throw new IOException("TCP admission rejected");
        } finally { deadline.cancel(false); }
    }

    private synchronized void release(Socket socket) {
        sockets.remove(socket);
        identities.values().removeIf(value -> value == socket);
    }

    /** Closes current sessions/reservations only; changing admission policy is the owner's responsibility. */
    public void disconnect(RemoteServerId id) {
        Socket socket;
        TcpChannel channel;
        synchronized (this) {
            socket = identities.get(id);
            channel = sockets.get(socket);
        }
        if (channel != null) channel.close();
        else if (socket != null) TcpWire.close(socket);
    }

    @Override public void close() throws IOException {
        Map<Socket, TcpChannel> current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            current = new HashMap<>(sockets);
            sockets.clear();
            identities.clear();
        }
        try {
            listener.close();
        } finally {
            current.forEach((socket, channel) -> {
                if (channel != null) channel.close();
                else TcpWire.close(socket);
            });
        }
    }
}
