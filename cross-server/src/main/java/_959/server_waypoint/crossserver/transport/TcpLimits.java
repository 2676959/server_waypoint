package _959.server_waypoint.crossserver.transport;

/** Local resource ceilings; peers cannot negotiate larger limits. */
public record TcpLimits(int connections, int handshakeMillis, int operationMillis,
                        int pendingRequests, int retainedBytes, int sessionMessages) {
    public static final TcpLimits DEFAULT = new TcpLimits(32, 10_000, 30_000, 64, 4_194_304, 65_536);

    public TcpLimits {
        if (connections < 1 || connections > 256 || handshakeMillis < 1 || handshakeMillis > 10_000
                || operationMillis < 1 || operationMillis > 300_000
                || pendingRequests < 1 || pendingRequests > 64
                || retainedBytes < 1 || retainedBytes > 16_777_216
                || sessionMessages < 1 || sessionMessages > 1_048_576) {
            throw new IllegalArgumentException("Invalid transport limits");
        }
    }
}
