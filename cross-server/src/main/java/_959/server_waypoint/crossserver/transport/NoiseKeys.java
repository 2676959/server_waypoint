package _959.server_waypoint.crossserver.transport;

import java.util.Arrays;

/** Raw X25519 key material supplied by the credential owner (step 7). Never serialized or logged. */
public final class NoiseKeys implements AutoCloseable {
    private final byte[] privateKey;
    private boolean closed;

    public NoiseKeys(byte[] privateKey) {
        if (privateKey.length != 32) throw new IllegalArgumentException("Invalid key length");
        this.privateKey = privateKey.clone();
    }

    synchronized byte[] copyPrivate() {
        if (closed) throw new IllegalStateException("Keys closed");
        return privateKey.clone();
    }

    @Override public synchronized void close() {
        closed = true;
        Arrays.fill(privateKey, (byte) 0);
    }

    @Override public String toString() { return "NoiseKeys[redacted]"; }
}
