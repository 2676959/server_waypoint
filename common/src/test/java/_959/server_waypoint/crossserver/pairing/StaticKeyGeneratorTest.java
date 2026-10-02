package _959.server_waypoint.crossserver.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticKeyGeneratorTest {
    @TempDir Path directory;

    @Test void generatesKeyWithNewBackendTemplate() throws Exception {
        Path publicFile = StaticKeyGenerator.generate(directory.toRealPath());
        assertTrue(Files.exists(publicFile));
        assertTrue(Files.exists(directory.resolve("credentials/static.key")));
    }

    @Test void generatesConfiguredBackendKeyWithoutReplacingIt() throws Exception {
        Files.writeString(directory.resolve("cross-server.json"),
                "{\"transportMode\":\"NOISE_KK\",\"credentialsDirectory\":\"backend-keys\"}");
        Path publicFile = StaticKeyGenerator.generate(directory.toRealPath());
        Path privateFile = directory.resolve("backend-keys/static.key");
        byte[] original = Files.readAllBytes(privateFile);
        String publicKey = Files.readString(publicFile).trim();
        assertEquals(CanonicalKey.publicFromPrivate(original), publicKey);

        assertThrows(java.io.IOException.class, () -> StaticKeyGenerator.generate(directory.toRealPath()));
        assertArrayEquals(original, Files.readAllBytes(privateFile));
        assertEquals(publicKey, Files.readString(publicFile).trim());
    }

    @Test void rejectsPlaintextWithoutCreatingCredentials() throws Exception {
        Files.writeString(directory.resolve("cross-server.json"), "{\"transportMode\":\"PLAINTEXT\"}");
        assertThrows(java.io.IOException.class, () -> StaticKeyGenerator.generate(directory));
        assertFalse(Files.exists(directory.resolve("credentials")));
    }
}
