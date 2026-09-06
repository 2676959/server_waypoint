package _959.server_waypoint.noisespike.kk;

import com.southernstorm.noise.protocol.CipherState;
import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.HandshakeState;

import java.util.Arrays;

/** Single-threaded, disposable dependency harness. Not a production transport API. */
public final class KkSession implements AutoCloseable {
    public static final String PROTOCOL = "Noise_KK_25519_AESGCM_SHA256";
    private final HandshakeState handshake;
    private CipherStatePair transport;
    private boolean closed;

    public KkSession(boolean initiator, byte[] localPrivate, byte[] remotePublic, byte[] prologue)
            throws Exception {
        handshake = new HandshakeState(PROTOCOL,
                initiator ? HandshakeState.INITIATOR : HandshakeState.RESPONDER);
        try {
            handshake.getLocalKeyPair().setPrivateKey(localPrivate, 0);
            handshake.getRemotePublicKey().setPublicKey(remotePublic, 0);
            handshake.setPrologue(prologue, 0, prologue.length);
            handshake.start();
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    public byte[] writeHandshake() throws Exception {
        requireOpen();
        try {
            byte[] message = new byte[65_535];
            return Arrays.copyOf(message, handshake.writeMessage(message, 0, null, 0, 0));
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    public void readHandshake(byte[] message) throws Exception {
        requireOpen();
        try {
            requireRecord(message.length);
            if (handshake.readMessage(message, 0, message.length, new byte[65_535], 0) != 0) {
                throw new Rejected("handshake-payload");
            }
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    public byte[] split() {
        requireOpen();
        byte[] hash = handshake.getHandshakeHash().clone();
        transport = handshake.split();
        handshake.destroy();
        return hash;
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        requireTransport();
        try {
            if (plaintext.length > 65_519) {
                throw new IllegalArgumentException("Oversized plaintext");
            }
            return encrypt(transport.getSender(), plaintext);
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    public byte[] decrypt(byte[] ciphertext) throws Exception {
        requireTransport();
        try {
            requireRecord(ciphertext.length);
            return decrypt(transport.getReceiver(), ciphertext);
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    static byte[] encrypt(CipherState cipher, byte[] plaintext) throws Exception {
        byte[] result = new byte[plaintext.length + 16];
        return Arrays.copyOf(result, cipher.encryptWithAd(null, plaintext, 0, result, 0, plaintext.length));
    }

    static byte[] decrypt(CipherState cipher, byte[] ciphertext) throws Exception {
        byte[] result = new byte[ciphertext.length];
        return Arrays.copyOf(result, cipher.decryptWithAd(null, ciphertext, 0, result, 0, ciphertext.length));
    }

    private static void requireRecord(int length) {
        if (length > 65_535) {
            throw new IllegalArgumentException("Oversized Noise record");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Closed probe session");
        }
    }

    private void requireTransport() {
        requireOpen();
        if (transport == null) {
            throw new IllegalStateException("Handshake incomplete");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            handshake.destroy();
            if (transport != null) {
                transport.destroy();
            }
        }
    }

    static final class Rejected extends Exception {
        Rejected(String reason) {
            super(reason);
        }
    }
}
