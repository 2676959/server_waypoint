package _959.server_waypoint.crossserver.pairing;

import _959.server_waypoint.crossserver.transport.NoiseKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** A single lifecycle owner uses this store. Plaintext owners must skip constructing it entirely. */
public final class LocalCredentials {
    private final CredentialFiles files;
    public LocalCredentials(CredentialFiles files) { this.files = files; }

    private byte[] load() throws IOException {
        byte[] encoded = files.read("static.key", 48);
        try {
            if (encoded == null) {
                if (files.read("coordinator.pin", 60) != null) throw new IOException("Paired private key missing");
                encoded = CanonicalKey.generatePrivate();
                files.write("static.key", encoded);
            }
            CanonicalKey.publicFromPrivate(encoded);
            return encoded;
        } catch (Exception failure) {
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
            throw new IOException("Private credential rejected");
        }
    }

    public synchronized String publicKey() throws IOException {
        byte[] encoded = load();
        try { return CanonicalKey.publicFromPrivate(encoded); } finally { Arrays.fill(encoded, (byte) 0); }
    }
    public synchronized NoiseKeys noiseKeys() throws IOException {
        byte[] encoded = load();
        try { return CanonicalKey.noiseKeys(encoded); } finally { Arrays.fill(encoded, (byte) 0); }
    }
    /** Administrative rotation; caller must revoke old sessions and pair again before connecting. */
    public synchronized void rotate() throws IOException {
        byte[] encoded = CanonicalKey.generatePrivate();
        try { files.write("static.key", encoded); } finally { Arrays.fill(encoded, (byte) 0); }
    }
    public synchronized String coordinatorPin() throws IOException {
        byte[] encoded = files.read("coordinator.pin", 60);
        if (encoded == null) return null;
        String pin = new String(encoded, StandardCharsets.US_ASCII);
        CanonicalKey.publicBytes(pin);
        return pin;
    }
    /** Called only after authenticated pairing completion. Explicit expectation prevents silent replacement. */
    synchronized void installPin(String expected, String pin) throws IOException {
        CanonicalKey.publicBytes(pin);
        if (!java.util.Objects.equals(expected, coordinatorPin())) throw new IOException("Coordinator pin changed");
        files.write("coordinator.pin", pin.getBytes(StandardCharsets.US_ASCII));
    }
    @Override public String toString() { return "LocalCredentials[redacted]"; }
}
