package _959.server_waypoint.crossserver.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CredentialTest {
    @TempDir Path temporary;
    @Test void rfc7748PublicDerivationAndCanonicalImport() {
        byte[] privateDer = HexFormat.of().parseHex("302e020100300506032b656e0422042077076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        String expected = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(
                "302a300506032b656e0321008520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"));
        assertEquals(expected, CanonicalKey.publicFromPrivate(privateDer));
        assertEquals(32, CanonicalKey.rawPublic(expected).length);
        for (String malformed : List.of(expected.trim() + "\n", expected.substring(0, 59), " " + expected, "A".repeat(60))) {
            assertThrows(IllegalArgumentException.class, () -> CanonicalKey.publicBytes(malformed));
        }
        byte[] zero = HexFormat.of().parseHex("302a300506032b656e032100" + "00".repeat(32));
        assertThrows(IllegalArgumentException.class, () -> CanonicalKey.publicBytes(Base64.getEncoder().encodeToString(zero)));
        zero[12] = 1;
        assertThrows(IllegalArgumentException.class, () -> CanonicalKey.publicBytes(Base64.getEncoder().encodeToString(zero)));
        Arrays.fill(zero, 12, 44, (byte) 255);
        assertThrows(IllegalArgumentException.class, () -> CanonicalKey.publicBytes(Base64.getEncoder().encodeToString(zero)));
        assertThrows(IllegalArgumentException.class, () -> CanonicalKey.publicFromPrivate(Arrays.copyOf(privateDer, 49)));
    }

    @Test void independentHmacFixtureAndSecretRedaction() {
        byte[] bytes = new byte[32]; for (int i = 0; i < 32; i++) bytes[i] = (byte) i;
        String text = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try (PairingCode code = PairingCode.importCode(text)) {
            assertEquals("665293997ea39b9e23eea0b9b44d7c8563b7fd2e3cf1c6b51e8b106d81f64364",
                    HexFormat.of().formatHex(code.authenticate(1, new byte[]{97, 98, 99})));
            assertFalse(code.toString().contains(text));
            assertThrows(IllegalArgumentException.class, () -> PairingCode.importCode("123456"));
            assertThrows(IllegalArgumentException.class, () -> PairingCode.importCode(text + "="));
            code.close(); assertThrows(IllegalStateException.class, code::exportCode);
        }
    }

    @Test void persistentUniqueKeysPrivatePermissionsAndExclusiveOwner() throws Exception {
        Path directory = temporary.toRealPath().resolve("credentials");
        String key;
        try (CredentialFiles files = new CredentialFiles(directory)) {
            LocalCredentials credentials = new LocalCredentials(files);
            key = credentials.publicKey();
            assertEquals(key, credentials.publicKey());
            assertEquals(48, Files.size(directory.resolve("static.key")));
            assertThrows(IOException.class, () -> new CredentialFiles(directory));
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory));
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("static.key")));
            }
        }
        try (CredentialFiles files = new CredentialFiles(directory);
             CredentialFiles other = new CredentialFiles(temporary.toRealPath().resolve("other"))) {
            LocalCredentials credentials = new LocalCredentials(files);
            assertEquals(key, credentials.publicKey());
            assertNotEquals(key, new LocalCredentials(other).publicKey());
            credentials.rotate(); assertNotEquals(key, credentials.publicKey());
        }
    }

    @Test void malformedMissingAndSymlinkCredentialsFailClosed() throws Exception {
        Path directory = temporary.toRealPath().resolve("credentials");
        try (CredentialFiles files = new CredentialFiles(directory)) {
            LocalCredentials credentials = new LocalCredentials(files);
            String key = credentials.publicKey();
            credentials.installPin(null, key);
            assertThrows(IOException.class, () -> credentials.installPin(null, key));
            Files.delete(directory.resolve("static.key"));
            assertThrows(IOException.class, credentials::publicKey);
            files.write("static.key", new byte[48]);
            assertThrows(IOException.class, credentials::publicKey);
            Files.delete(directory.resolve("static.key"));
            Files.createSymbolicLink(directory.resolve("static.key"), temporary.resolve("outside"));
            assertThrows(IOException.class, credentials::publicKey);
            assertThrows(IOException.class, () -> files.write("static.key", new byte[48]));
        }
    }

    @Test void plaintextSkipsCredentialsAndStillRejectsRemoteEndpoint() throws Exception {
        Path unused = temporary.toRealPath().resolve("must-not-exist");
        assertTrue(CredentialFiles.forTransport(_959.server_waypoint.crossserver.transport.TransportMode.PLAINTEXT,
                new _959.server_waypoint.crossserver.transport.TcpEndpoint("127.0.0.1", 1), unused).isEmpty());
        assertFalse(Files.exists(unused));
        assertThrows(IllegalArgumentException.class, () -> CredentialFiles.forTransport(
                _959.server_waypoint.crossserver.transport.TransportMode.PLAINTEXT,
                new _959.server_waypoint.crossserver.transport.TcpEndpoint("example.com", 1), unused));
        assertFalse(Files.exists(unused));
    }
}
