package _959.server_waypoint.crossserver.transport;

import com.southernstorm.noise.protocol.HandshakeState;
import com.southernstorm.noise.protocol.CipherStatePair;
import java.net.Socket;
import java.io.IOException;
import java.util.Arrays;

/** Serializes all cipher access and destruction; no socket I/O under this monitor. */
final class NoiseRecordCipher implements AutoCloseable {
    private CipherStatePair pair;
    private long sent;
    private long received;

    static NoiseRecordCipher handshake(Socket socket, boolean backend, NoiseKeys keys, byte[] pin,
                                        byte[] prologue) throws Exception {
        if (pin == null || pin.length != 32) throw new IOException("Missing public pin");
        HandshakeState state = new HandshakeState("Noise_KK_25519_AESGCM_SHA256",
                backend ? HandshakeState.INITIATOR : HandshakeState.RESPONDER);
        NoiseRecordCipher cipher = new NoiseRecordCipher();
        byte[] secret = null;
        try {
            secret = keys.copyPrivate();
            state.getLocalKeyPair().setPrivateKey(secret, 0);
            state.getRemotePublicKey().setPublicKey(pin, 0);
            state.setPrologue(prologue, 0, prologue.length);
            state.start();
            if (backend) {
                writeHandshake(socket, state);
                readHandshake(socket, state);
            } else {
                readHandshake(socket, state);
                writeHandshake(socket, state);
            }
            byte[] hash = state.getHandshakeHash().clone();
            cipher.pair = state.split();
            // A separate encrypted acknowledgement also prevents exposing a backend channel before admission.
            byte[] confirmation = new byte[33];
            System.arraycopy(hash, 0, confirmation, 1, 32);
            confirmation[0] = 1;
            if (backend) {
                TcpWire.write(socket, cipher.encrypt(confirmation), TcpWire.RECORD);
                confirmation[0] = 2;
                if (!Arrays.equals(confirmation, cipher.decrypt(TcpWire.read(socket, TcpWire.RECORD)))) {
                    throw new IOException("Invalid confirmation");
                }
            } else {
                if (!Arrays.equals(confirmation, cipher.decrypt(TcpWire.read(socket, TcpWire.RECORD)))) {
                    throw new IOException("Invalid confirmation");
                }
                confirmation[0] = 2;
                TcpWire.write(socket, cipher.encrypt(confirmation), TcpWire.RECORD);
            }
            return cipher;
        } catch (Exception failure) {
            cipher.close();
            throw failure;
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            state.destroy();
        }
    }

    private static void writeHandshake(Socket socket, HandshakeState state) throws Exception {
        byte[] bytes = new byte[TcpWire.RECORD];
        int length = state.writeMessage(bytes, 0, null, 0, 0);
        TcpWire.write(socket, Arrays.copyOf(bytes, length), TcpWire.RECORD);
    }

    private static void readHandshake(Socket socket, HandshakeState state) throws Exception {
        byte[] bytes = TcpWire.read(socket, TcpWire.RECORD);
        if (state.readMessage(bytes, 0, bytes.length, new byte[TcpWire.RECORD], 0) != 0) {
            throw new IOException("Handshake payload forbidden");
        }
    }

    synchronized byte[] encrypt(byte[] clear) throws Exception {
        if (pair == null || sent == Long.MAX_VALUE || clear.length > TcpWire.CLEAR) throw new IOException("Cipher exhausted or closed");
        byte[] result = new byte[clear.length + 16];
        pair.getSender().encryptWithAd(null, clear, 0, result, 0, clear.length);
        sent++;
        return result;
    }

    synchronized byte[] decrypt(byte[] encrypted) throws Exception {
        if (pair == null || received == Long.MAX_VALUE || encrypted.length < 16 || encrypted.length > TcpWire.RECORD) {
            throw new IOException("Cipher exhausted or closed");
        }
        byte[] result = new byte[encrypted.length - 16];
        pair.getReceiver().decryptWithAd(null, encrypted, 0, result, 0, encrypted.length);
        received++;
        return result;
    }

    @Override public synchronized void close() {
        if (pair != null) { pair.destroy(); pair = null; }
    }
}
