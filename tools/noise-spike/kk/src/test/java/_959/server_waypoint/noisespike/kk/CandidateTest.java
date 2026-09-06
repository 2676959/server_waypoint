package _959.server_waypoint.noisespike.kk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.HandshakeState;
import com.southernstorm.noise.protocol.Noise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.BadPaddingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class CandidateTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void matchesPublishedKkVector(boolean fallback) throws Exception {
        Noise.setForceFallbacks(fallback);
        try {
            JsonArray vectors;
            try (var reader = Files.newBufferedReader(Path.of(System.getProperty("spike.vectorFile")))) {
                vectors = JsonParser.parseReader(reader).getAsJsonArray();
            }
            int matching = 0;
            for (var element : vectors) {
                JsonObject vector = element.getAsJsonObject();
                if (!KkSession.PROTOCOL.equals(vector.get("protocol_name").getAsString())) {
                    continue;
                }
                matching++;
                HandshakeState initiator = vectorHandshake(vector, true);
                HandshakeState responder = vectorHandshake(vector, false);
                CipherStatePair initiatorTransport = null;
                CipherStatePair responderTransport = null;
                try {
                    JsonArray messages = vector.getAsJsonArray("messages");
                    assertEquals(6, messages.size());
                    for (int i = 0; i < messages.size(); i++) {
                        JsonObject message = messages.get(i).getAsJsonObject();
                        byte[] plaintext = hex(message, "payload");
                        byte[] expected = hex(message, "ciphertext");
                        boolean outbound = i % 2 == 0;
                        byte[] actual;
                        byte[] decrypted;
                        if (i < 2) {
                            actual = write(outbound ? initiator : responder, plaintext);
                            var recipient = outbound ? responder : initiator;
                            byte[] buffer = new byte[65_535];
                            decrypted = Arrays.copyOf(buffer, recipient.readMessage(actual, 0, actual.length, buffer, 0));
                        } else {
                            actual = KkSession.encrypt((outbound ? initiatorTransport : responderTransport).getSender(), plaintext);
                            decrypted = KkSession.decrypt((outbound ? responderTransport : initiatorTransport).getReceiver(), actual);
                        }
                        assertArrayEquals(expected, actual, "Published message " + i);
                        assertArrayEquals(plaintext, decrypted);
                        if (i == 1) {
                            assertEquals(HandshakeState.SPLIT, initiator.getAction());
                            assertEquals(HandshakeState.SPLIT, responder.getAction());
                            assertArrayEquals(hex(vector, "handshake_hash"), initiator.getHandshakeHash());
                            assertArrayEquals(initiator.getHandshakeHash(), responder.getHandshakeHash());
                            initiatorTransport = initiator.split();
                            responderTransport = responder.split();
                        }
                    }
                } finally {
                    initiator.destroy();
                    responder.destroy();
                    if (initiatorTransport != null) {
                        initiatorTransport.destroy();
                    }
                    if (responderTransport != null) {
                        responderTransport.destroy();
                    }
                }
            }
            assertEquals(1, matching, "Exact suite must exist in pinned external corpus");
        } finally {
            Noise.setForceFallbacks(false);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nonceBoundaryFailsClosedForEncryptionAndDecryption(boolean fallback) throws Exception {
        Noise.setForceFallbacks(fallback);
        var sender = Noise.createCipher("AESGCM");
        var receiver = Noise.createCipher("AESGCM");
        try {
            sender.initializeKey(new byte[32], 0);
            receiver.initializeKey(new byte[32], 0);
            sender.setNonce(-2L); // Last permitted unsigned nonce, 2^64 - 2.
            receiver.setNonce(-2L);
            byte[] ciphertext = KkSession.encrypt(sender, new byte[]{42});
            assertArrayEquals(new byte[]{42}, KkSession.decrypt(receiver, ciphertext));
            for (int attempt = 0; attempt < 2; attempt++) {
                byte[] encrypted = new byte[17];
                byte[] decrypted = new byte[1];
                Arrays.fill(encrypted, (byte) 73);
                Arrays.fill(decrypted, (byte) 73);
                assertThrows(IllegalStateException.class,
                        () -> sender.encryptWithAd(null, new byte[]{42}, 0, encrypted, 0, 1));
                assertThrows(IllegalStateException.class,
                        () -> receiver.decryptWithAd(null, ciphertext, 0, decrypted, 0, ciphertext.length));
                assertArrayEquals(new byte[]{73}, decrypted);
                for (byte value : encrypted) {
                    assertEquals(73, value, "Exhausted cipher wrote output");
                }
            }
        } finally {
            sender.destroy();
            receiver.destroy();
            Noise.setForceFallbacks(false);
        }
    }

    @Test
    void badTagLeavesOutputUntouchedAndHarnessDiscardsSession() throws Exception {
        KkSession[] peers = peers();
        try (var initiator = peers[0]; var responder = peers[1]) {
            byte[] valid = initiator.encrypt(new byte[]{1, 2, 3});
            byte[] corrupted = valid.clone();
            corrupted[corrupted.length - 1] ^= 1;
            assertThrows(BadPaddingException.class, () -> responder.decrypt(corrupted));
            assertThrows(IllegalStateException.class, () -> responder.decrypt(valid));
        }
        var cipher = Noise.createCipher("AESGCM");
        try {
            cipher.initializeKey(new byte[32], 0);
            byte[] output = new byte[]{73};
            assertThrows(BadPaddingException.class,
                    () -> cipher.decryptWithAd(null, new byte[17], 0, output, 0, 17));
            assertArrayEquals(new byte[]{73}, output);
        } finally {
            cipher.destroy();
        }
    }

    @Test
    void wrapperEnforcesRecordBudgetAndClosesOnOversize() throws Exception {
        KkSession[] peers = peers();
        try (var initiator = peers[0]; var responder = peers[1]) {
            byte[] ciphertext = initiator.encrypt(new byte[65_519]);
            assertEquals(65_535, ciphertext.length);
            assertEquals(65_519, responder.decrypt(ciphertext).length);
            assertThrows(IllegalArgumentException.class, () -> initiator.encrypt(new byte[65_520]));
            assertThrows(IllegalStateException.class, () -> initiator.encrypt(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> responder.decrypt(new byte[65_536]));
            assertThrows(IllegalStateException.class, () -> responder.decrypt(ciphertext));
        }
    }

    @Test
    void replayedFirstHandshakeDoesNotAuthenticateTransportOnFreshResponder() throws Exception {
        try (Keys keys = new Keys()) {
            byte[] prologue = new byte[]{1, 2, 3};
            try (var initiator = new KkSession(true, keys.initiatorPrivate, keys.responderPublic, prologue);
                 var responder = new KkSession(false, keys.responderPrivate, keys.initiatorPublic, prologue);
                 var fresh = new KkSession(false, keys.responderPrivate, keys.initiatorPublic, prologue)) {
                byte[] first = initiator.writeHandshake();
                responder.readHandshake(first);
                initiator.readHandshake(responder.writeHandshake());
                initiator.split();
                responder.split();
                byte[] oldConfirmation = initiator.encrypt(new byte[]{7});
                assertArrayEquals(new byte[]{7}, responder.decrypt(oldConfirmation));
                // KK's first message is replayable. A fresh responder accepts it, but uses a fresh ephemeral.
                fresh.readHandshake(first);
                fresh.writeHandshake();
                fresh.split();
                assertThrows(BadPaddingException.class, () -> fresh.decrypt(oldConfirmation));
            }
        }
    }

    @Test
    void harnessRejectsNonemptyHandshakePayloadAndCannotSendTransportBeforeSplit() throws Exception {
        try (Keys keys = new Keys()) {
            var sender = new HandshakeState(KkSession.PROTOCOL, HandshakeState.INITIATOR);
            try (var receiver = new KkSession(false, keys.responderPrivate, keys.initiatorPublic, new byte[0])) {
                sender.getLocalKeyPair().setPrivateKey(keys.initiatorPrivate, 0);
                sender.getRemotePublicKey().setPublicKey(keys.responderPublic, 0);
                sender.start();
                assertThrows(IllegalStateException.class, () -> receiver.encrypt(new byte[0]));
                byte[] earlyOperation = write(sender, new byte[]{9});
                assertThrows(KkSession.Rejected.class, () -> receiver.readHandshake(earlyOperation));
                assertThrows(IllegalStateException.class, receiver::writeHandshake);
            } finally {
                sender.destroy();
            }
        }
    }

    private static HandshakeState vectorHandshake(JsonObject vector, boolean initiator) throws Exception {
        String prefix = initiator ? "init_" : "resp_";
        var state = new HandshakeState(KkSession.PROTOCOL,
                initiator ? HandshakeState.INITIATOR : HandshakeState.RESPONDER);
        byte[] prologue = hex(vector, prefix + "prologue");
        state.setPrologue(prologue, 0, prologue.length);
        state.getLocalKeyPair().setPrivateKey(hex(vector, prefix + "static"), 0);
        state.getRemotePublicKey().setPublicKey(hex(vector, prefix + "remote_static"), 0);
        state.getFixedEphemeralKey().setPrivateKey(hex(vector, prefix + "ephemeral"), 0);
        state.start();
        return state;
    }

    private static byte[] write(HandshakeState state, byte[] payload) throws Exception {
        byte[] message = new byte[65_535];
        return Arrays.copyOf(message, state.writeMessage(message, 0, payload, 0, payload.length));
    }

    private static byte[] hex(JsonObject object, String key) {
        return HexFormat.of().parseHex(object.get(key).getAsString());
    }

    private static KkSession[] peers() throws Exception {
        try (Keys keys = new Keys()) {
            var initiator = new KkSession(true, keys.initiatorPrivate, keys.responderPublic, new byte[0]);
            var responder = new KkSession(false, keys.responderPrivate, keys.initiatorPublic, new byte[0]);
            try {
                responder.readHandshake(initiator.writeHandshake());
                initiator.readHandshake(responder.writeHandshake());
                initiator.split();
                responder.split();
                return new KkSession[]{initiator, responder};
            } catch (Exception failure) {
                initiator.close();
                responder.close();
                throw failure;
            }
        }
    }

    private static final class Keys implements AutoCloseable {
        private final byte[] initiatorPrivate = new byte[32];
        private final byte[] responderPrivate = new byte[32];
        private final byte[] initiatorPublic = new byte[32];
        private final byte[] responderPublic = new byte[32];

        private Keys() throws Exception {
            var keys = Noise.createDH("25519");
            try {
                keys.generateKeyPair();
                keys.getPrivateKey(initiatorPrivate, 0);
                keys.getPublicKey(initiatorPublic, 0);
                keys.generateKeyPair();
                keys.getPrivateKey(responderPrivate, 0);
                keys.getPublicKey(responderPublic, 0);
            } finally {
                keys.destroy();
            }
        }

        @Override
        public void close() {
            Arrays.fill(initiatorPrivate, (byte) 0);
            Arrays.fill(responderPrivate, (byte) 0);
        }
    }
}
