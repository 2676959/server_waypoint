package com.eatthepath.noise;

import _959.server_waypoint.noisespike.NoisePeer;
import com.eatthepath.noise.component.NoiseCipher;
import com.eatthepath.noise.component.NoiseKeyAgreement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.southernstorm.noise.protocol.HandshakeState;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateTest {
    @Test
    void matchesPublishedNkpsk0AesGcmSha256Vector() throws Exception {
        JsonArray vectors;
        try (var reader = Files.newBufferedReader(Path.of(System.getProperty("spike.vectorFile")))) {
            vectors = JsonParser.parseReader(reader).getAsJsonArray();
        }
        int matching = 0;
        for (var element : vectors) {
            JsonObject vector = element.getAsJsonObject();
            if (!NoisePeer.PROTOCOL.equals(vector.get("protocol_name").getAsString())) {
                continue;
            }
            matching++;
            NoiseHandshake initiator = vectorHandshake(vector, true);
            NoiseHandshake responder = vectorHandshake(vector, false);
            NoiseTransport initiatorTransport = null;
            NoiseTransport responderTransport = null;
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
                    actual = (outbound ? initiator : responder).writeMessage(plaintext);
                    decrypted = (outbound ? responder : initiator).readMessage(actual);
                } else {
                    actual = (outbound ? initiatorTransport : responderTransport).writeMessage(plaintext);
                    decrypted = (outbound ? responderTransport : initiatorTransport).readMessage(actual);
                }
                assertArrayEquals(expected, actual, "Published message " + i);
                assertArrayEquals(plaintext, decrypted);
                if (i == 1) {
                    assertTrue(initiator.isDone());
                    assertTrue(responder.isDone());
                    initiatorTransport = initiator.toTransport();
                    responderTransport = responder.toTransport();
                    assertArrayEquals(hex(vector, "handshake_hash"), initiator.getHash());
                    assertArrayEquals(initiator.getHash(), responder.getHash());
                }
            }
        }
        assertEquals(1, matching, "The pinned corpus must contain the exact required suite");
    }

    @Test
    void rejectsTamperedAndReplayedTransportMessages() throws Exception {
        NoiseTransport[] peers = peers();
        byte[] ciphertext = peers[0].writeMessage(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, peers[1].readMessage(ciphertext));
        assertThrows(AEADBadTagException.class, () -> peers[1].readMessage(ciphertext));

        // A failed session is discarded, not retried with another suite or reused.
        NoiseTransport[] fresh = peers();
        byte[] tampered = fresh[1].writeMessage(new byte[]{4, 5, 6});
        tampered[tampered.length - 1] ^= 1;
        assertThrows(AEADBadTagException.class, () -> fresh[0].readMessage(tampered));
    }

    @Test
    void enforcesNoiseRecordLimitRatherThanOneMebibyteApplicationLimit() throws Exception {
        NoiseTransport[] peers = peers();
        assertEquals(65_535, peers[0].writeMessage(new byte[65_519]).length);
        assertThrows(IllegalArgumentException.class, () -> peers[0].writeMessage(new byte[65_520]));
        assertThrows(IllegalArgumentException.class, () -> peers[1].readMessage(new byte[65_536]));
    }

    @Test
    void recordsKnownNonceExhaustionGapInCandidate() throws Exception {
        // Characterization of a rejection reason, NOT an acceptance requirement or a production workaround.
        CipherState state = new CipherState(NoiseCipher.getInstance("AESGCM"));
        state.setKey(new byte[32]);
        var nonce = CipherState.class.getDeclaredField("nonce");
        nonce.setAccessible(true);
        nonce.setLong(state, -1L); // Unsigned 2^64 - 1 is reserved by Noise for rekeying.
        assertEquals(16, state.encrypt(null, new byte[0]).length,
                "Pinned candidate unexpectedly changed; reevaluate the dependency decision");
        assertEquals(0L, nonce.getLong(state), "Candidate wraps instead of failing closed");
    }

    @Test
    void signalReleaseCannotConstructRequiredPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeState(NoisePeer.PROTOCOL, HandshakeState.INITIATOR));
    }

    private static NoiseHandshake vectorHandshake(JsonObject vector, boolean initiator) throws Exception {
        String prefix = initiator ? "init_" : "resp_";
        var builder = new NamedProtocolHandshakeBuilder(NoisePeer.PROTOCOL,
                initiator ? NoiseHandshake.Role.INITIATOR : NoiseHandshake.Role.RESPONDER)
                .setPrologue(hex(vector, prefix + "prologue"))
                .setPreSharedKeys(List.of(HexFormat.of().parseHex(
                        vector.getAsJsonArray(prefix + "psks").get(0).getAsString())))
                .setLocalEphemeralKeyPair(rawKeyPair(hex(vector, prefix + "ephemeral")));
        if (initiator) {
            builder.setRemoteStaticPublicKey(NoiseKeyAgreement.getInstance("25519")
                    .deserializePublicKey(hex(vector, "init_remote_static")));
        } else {
            builder.setLocalStaticKeyPair(rawKeyPair(hex(vector, "resp_static")));
        }
        return builder.build();
    }

    private static KeyPair rawKeyPair(byte[] scalar) throws Exception {
        // Deterministic key import only for the public upstream test vector, using JDK X25519.
        var factory = KeyFactory.getInstance("X25519");
        var privateKey = factory.generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, scalar));
        var basePoint = factory.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, BigInteger.valueOf(9)));
        var agreement = KeyAgreement.getInstance("X25519");
        agreement.init(privateKey);
        agreement.doPhase(basePoint, true);
        return new KeyPair(NoiseKeyAgreement.getInstance("25519").deserializePublicKey(agreement.generateSecret()),
                privateKey);
    }

    private static byte[] hex(JsonObject object, String key) {
        return HexFormat.of().parseHex(object.get(key).getAsString());
    }

    private static NoiseTransport[] peers() throws Exception {
        var keys = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        var initiator = new NamedProtocolHandshakeBuilder(NoisePeer.PROTOCOL, NoiseHandshake.Role.INITIATOR)
                .setRemoteStaticPublicKey(keys.getPublic()).setPreSharedKeys(List.of(new byte[32])).build();
        var responder = new NamedProtocolHandshakeBuilder(NoisePeer.PROTOCOL, NoiseHandshake.Role.RESPONDER)
                .setLocalStaticKeyPair(keys).setPreSharedKeys(List.of(new byte[32])).build();
        responder.readMessage(initiator.writeMessage(new byte[0]));
        initiator.readMessage(responder.writeMessage(new byte[0]));
        return new NoiseTransport[]{initiator.toTransport(), responder.toTransport()};
    }
}
