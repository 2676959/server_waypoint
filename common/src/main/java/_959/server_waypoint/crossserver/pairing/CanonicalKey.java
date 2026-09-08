package _959.server_waypoint.crossserver.pairing;

import _959.server_waypoint.crossserver.transport.NoiseKeys;
import java.security.*;
import java.security.spec.*;
import javax.crypto.KeyAgreement;
import java.util.*;

/** Strict RFC 8410 X25519 SPKI and minimal PKCS#8. No PEM, aliases or alternate DER encodings. */
public final class CanonicalKey {
    private static final byte[] PUBLIC_PREFIX = HexFormat.of().parseHex("302a300506032b656e032100");
    private static final byte[] PRIVATE_PREFIX = HexFormat.of().parseHex("302e020100300506032b656e04220420");

    public static byte[] publicBytes(String encoded) {
        try {
            if (encoded.length() != 60) throw invalid();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (!Base64.getEncoder().encodeToString(bytes).equals(encoded)) throw invalid();
            prefix(bytes, PUBLIC_PREFIX);
            byte[] raw = Arrays.copyOfRange(bytes, PUBLIC_PREFIX.length, bytes.length);
            byte[] reversed = raw.clone();
            for (int i = 0; i < 16; i++) { byte b = reversed[i]; reversed[i] = reversed[31-i]; reversed[31-i] = b; }
            if (new java.math.BigInteger(1, reversed).compareTo(java.math.BigInteger.ONE.shiftLeft(255)
                    .subtract(java.math.BigInteger.valueOf(19))) >= 0) throw invalid();
            // JDK X25519 rejects small-order inputs which produce an all-zero shared secret.
            KeyPair probe = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            agreement(probe.getPrivate(), bytes);
            return bytes;
        } catch (GeneralSecurityException | IllegalArgumentException failure) { throw invalid(); }
    }

    public static byte[] rawPublic(String encoded) {
        byte[] bytes = publicBytes(encoded);
        return Arrays.copyOfRange(bytes, PUBLIC_PREFIX.length, bytes.length);
    }

    public static String publicFromPrivate(byte[] encoded) {
        try {
            prefix(encoded, PRIVATE_PREFIX);
            PrivateKey key = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            byte[] base = new byte[44];
            System.arraycopy(PUBLIC_PREFIX, 0, base, 0, PUBLIC_PREFIX.length);
            base[PUBLIC_PREFIX.length] = 9;
            byte[] raw = agreement(key, base);
            System.arraycopy(raw, 0, base, PUBLIC_PREFIX.length, 32);
            return Base64.getEncoder().encodeToString(base);
        } catch (GeneralSecurityException failure) { throw invalid(); }
    }

    public static NoiseKeys noiseKeys(byte[] encoded) {
        prefix(encoded, PRIVATE_PREFIX);
        byte[] raw = Arrays.copyOfRange(encoded, PRIVATE_PREFIX.length, encoded.length);
        try { return new NoiseKeys(raw); } finally { Arrays.fill(raw, (byte) 0); }
    }

    public static byte[] generatePrivate() {
        try {
            byte[] encoded = KeyPairGenerator.getInstance("X25519").generateKeyPair().getPrivate().getEncoded();
            prefix(encoded, PRIVATE_PREFIX);
            return encoded;
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("X25519 unavailable"); }
    }

    private static byte[] agreement(PrivateKey key, byte[] publicDer) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(key);
        agreement.doPhase(KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(publicDer)), true);
        return agreement.generateSecret();
    }

    private static void prefix(byte[] encoded, byte[] prefix) {
        if (encoded.length != prefix.length + 32 || !Arrays.equals(prefix, Arrays.copyOf(encoded, prefix.length))) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid canonical X25519 key"); }
    private CanonicalKey() { }
}
