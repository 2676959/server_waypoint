package _959.server_waypoint.crossserver.pairing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.*;

/** High-entropy out-of-band secret. Never accept human passwords or short numeric codes. */
public final class PairingCode implements AutoCloseable {
    private final byte[] secret;
    private boolean closed;
    private PairingCode(byte[] secret) { this.secret = secret; }
    public static PairingCode generate() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return new PairingCode(bytes);
    }
    public static PairingCode importCode(String text) {
        try {
            if (text.length() != 43) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(text);
            if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(text)) {
                throw new IllegalArgumentException();
            }
            return new PairingCode(bytes);
        } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Invalid pairing code"); }
    }
    /** Explicit secret reveal for delivery over an authenticated confidential administrative path. */
    public synchronized String exportCode() {
        requireOpen(); return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
    public synchronized byte[] authenticate(int phase, byte[] transcript) {
        requireOpen();
        if (phase < 1 || phase > 4 || transcript.length > 512) throw new IllegalArgumentException("Invalid pairing transcript");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update("ServerWaypoint pairing v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            mac.update((byte) phase);
            return mac.doFinal(transcript);
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("HMAC unavailable"); }
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Pairing code closed"); }
    @Override public synchronized void close() { closed = true; Arrays.fill(secret, (byte) 0); }
    @Override public String toString() { return "PairingCode[redacted]"; }
}
