package _959.server_waypoint.crossserver.transport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public record TcpEndpoint(String host, int port) {
    public TcpEndpoint {
        Objects.requireNonNull(host, "host");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
    }

    InetSocketAddress resolve(TransportMode mode) throws UnknownHostException {
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (mode == TransportMode.PLAINTEXT) {
            // No DNS names, scoped addresses, abbreviated IPv4 or wildcard bypass.
            if (!literal.equals("::1") && !literal.equals("0:0:0:0:0:0:0:1")
                    && !literal.matches("127\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})")) {
                throw new IllegalArgumentException("Plaintext requires a literal loopback address");
            }
        }
        InetAddress address = InetAddress.getByName(literal);
        if (mode == TransportMode.PLAINTEXT && !address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Plaintext requires loopback");
        }
        return new InetSocketAddress(address, port);
    }
}
