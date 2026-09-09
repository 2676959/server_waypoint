package _959.server_waypoint.crossserver;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigurationTest {
    @TempDir Path directory;
    @Test void createsDisabledNoiseDefaultWithoutCredentials() throws Exception {
        var config = RuntimeConfiguration.read(directory, Set.of("enabled", "transportMode"));
        assertFalse(RuntimeConfiguration.enabled(config));
        assertEquals("NOISE_KK", RuntimeConfiguration.mode(config).name());
        assertFalse(Files.exists(directory.resolve("credentials")));
    }
    @Test void rejectsUnknownFieldsOversizedFilesAndUnsupportedProtocol() throws Exception {
        Path file = directory.resolve("cross-server.json");
        Files.writeString(file, "{\"secret\":\"hidden\"}");
        assertThrows(Exception.class, () -> RuntimeConfiguration.read(directory, Set.of("enabled")));
        Files.writeString(file, "{\"protocolVersion\":999}");
        assertThrows(Exception.class, () -> RuntimeConfiguration.read(directory, Set.of("protocolVersion")));
        Files.writeString(file, " ".repeat(1048577));
        assertThrows(Exception.class, () -> RuntimeConfiguration.read(directory, Set.of()));
    }
    @Test void plaintextRequiresLiteralLoopbackAndNoCryptoConfiguration() throws Exception {
        var config = JsonParser.parseString("{\"transportMode\":\"PLAINTEXT\",\"coordinator\":\"127.0.0.1:25580\"}").getAsJsonObject();
        assertEquals(25580, RuntimeConfiguration.endpoint(config, "coordinator").port());
        for (String endpoint : List.of("localhost:25580", "0.0.0.0:25580", "192.0.2.1:25580")) {
            config.addProperty("coordinator", endpoint); assertThrows(Exception.class, () -> RuntimeConfiguration.endpoint(config, "coordinator"));
        }
        for (String key : List.of("credentialsDirectory", "coordinatorPublicKey", "requiredSuite")) {
            config.addProperty(key, "value"); assertThrows(Exception.class, () -> RuntimeConfiguration.rejectCryptoInPlaintext(config)); config.remove(key);
        }
    }
}
