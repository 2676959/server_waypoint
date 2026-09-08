package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/** One explicit outbound connection attempt. Reconnect and platform lifecycle belong to step 8. */
public final class TcpBackend {
    public static TcpChannel connect(TcpEndpoint endpoint, TransportMode mode, RemoteServerId id,
                                     Set<Integer> capabilities, NoiseKeys keys, byte[] coordinatorPin,
                                     TcpLimits limits, ProtocolLimits protocol) throws IOException {
        return connect(endpoint, mode, id, capabilities, keys, coordinatorPin, limits, protocol, socket -> { });
    }

    static TcpChannel connect(TcpEndpoint endpoint, TransportMode mode, RemoteServerId id,
                              Set<Integer> capabilities, NoiseKeys keys, byte[] coordinatorPin,
                              TcpLimits limits, ProtocolLimits protocol,
                              java.util.function.Consumer<Socket> owner) throws IOException {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(protocol, "protocol");
        var address = endpoint.resolve(mode);
        if (mode == TransportMode.NOISE_KK && (keys == null || coordinatorPin == null || coordinatorPin.length != 32)
                || mode == TransportMode.PLAINTEXT && (keys != null || coordinatorPin != null)) {
            throw new IllegalArgumentException("Credentials must match transport mode");
        }
        byte[] pin = coordinatorPin == null ? null : coordinatorPin.clone();
        Socket socket = new Socket();
        ScheduledFuture<?> deadline = TcpWire.deadline(socket, limits.handshakeMillis());
        NoiseRecordCipher cipher = null;
        try {
            owner.accept(socket);
            socket.connect(address, limits.handshakeMillis());
            socket.setTcpNoDelay(true);
            if (mode == TransportMode.PLAINTEXT && (!socket.getInetAddress().isLoopbackAddress()
                    || !socket.getLocalAddress().isLoopbackAddress())) throw new IOException("Non-loopback socket");
            byte[] first = TcpWire.hello(mode, id, capabilities, false);
            TcpWire.write(socket, first, 65_536);
            byte[] second = TcpWire.read(socket, 65_536);
            TcpWire.Hello hello = TcpWire.parse(second, mode, true);
            if (!hello.serverId().equals(id) || !hello.capabilities().equals(capabilities)) throw new IOException("Preface changed");
            if (mode == TransportMode.NOISE_KK) {
                cipher = NoiseRecordCipher.handshake(socket, true, keys, pin, TcpWire.prologue(first, second));
            }
            if (socket.isClosed()) throw new IOException("Handshake expired");
            return new TcpChannel(socket, mode, hello, cipher, limits, protocol, () -> { });
        } catch (Exception failure) {
            TcpWire.close(socket);
            if (cipher != null) cipher.close();
            throw new IOException("TCP connection rejected");
        } finally { deadline.cancel(false); }
    }

    private TcpBackend() { }
}
