package _959.server_waypoint.crossserver.transport;

/** Plaintext trusts local processes and never authenticates identity. */
public enum TransportMode {
    NOISE_KK(1), PLAINTEXT(2);

    final int wireId;
    TransportMode(int wireId) { this.wireId = wireId; }
}
