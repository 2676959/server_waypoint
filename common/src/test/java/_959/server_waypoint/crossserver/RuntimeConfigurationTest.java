package _959.server_waypoint.crossserver;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigurationTest {
    @TempDir Path directory;
    @Test void createsEditableDisabledBackendTemplateWithoutCredentials() throws Exception {
        var config = RuntimeConfiguration.readBackend(directory);
        assertFalse(RuntimeConfiguration.enabled(config));
        assertEquals("NOISE_KK", RuntimeConfiguration.mode(config).name());
        assertEquals(Set.of("enabled", "transportMode", "serverId", "coordinator", "protocolVersion",
                "requiredSuite", "credentialsDirectory", "coordinatorPublicKey", "catalogExport", "serverIconItem"),
                config.keySet());
        assertEquals(CrossServerProtocol.PROTOCOL_VERSION, config.get("protocolVersion").getAsInt());
        assertEquals("credentials", config.get("credentialsDirectory").getAsString());
        assertEquals("PUBLIC", config.get("catalogExport").getAsString());
        assertEquals(ServerIcon.DEFAULT, config.get("serverIconItem").getAsString());
        assertFalse(Files.exists(directory.resolve("credentials")));
    }
    @Test void createsEditableDisabledCoordinatorTemplateWithoutCredentials() throws Exception {
        var config = RuntimeConfiguration.readCoordinator(directory);
        assertFalse(RuntimeConfiguration.enabled(config));
        assertEquals(Set.of("enabled", "transportMode", "listen", "protocolVersion", "requiredSuite",
                "credentialsDirectory", "proxyPermission", "backends"), config.keySet());
        assertEquals(CrossServerProtocol.PROTOCOL_VERSION, config.get("protocolVersion").getAsInt());
        var example = config.getAsJsonObject("backends").getAsJsonObject("replace-with-server-id");
        assertNotNull(example);
        assertFalse(RuntimeConfiguration.enabled(example));
        assertEquals("replace-with-velocity-server-name", example.get("velocityServer").getAsString());
        assertFalse(Files.exists(directory.resolve("credentials")));
    }
    @Test void rejectsUnknownFieldsOversizedFilesAndUnsupportedProtocol() throws Exception {
        Path file = directory.resolve("cross-server.json");
        Files.writeString(file, "{\"secret\":\"hidden\"}");
        assertThrows(Exception.class, () -> RuntimeConfiguration.readBackend(directory));
        Files.writeString(file, "{\"protocolVersion\":999}");
        assertThrows(Exception.class, () -> RuntimeConfiguration.readCoordinator(directory));
        Files.writeString(file, " ".repeat(1048577));
        assertThrows(Exception.class, () -> RuntimeConfiguration.readBackend(directory));
    }
    @Test void doesNotRewriteExistingConfiguration() throws Exception {
        Path file = directory.resolve("cross-server.json");
        String existing = "{\"enabled\":false}";
        Files.writeString(file, existing);
        RuntimeConfiguration.readBackend(directory);
        assertEquals(existing, Files.readString(file));
    }
    @Test void plaintextRequiresLiteralLoopbackAndNoCryptoConfiguration() throws Exception {
        var config = JsonParser.parseString("{\"transportMode\":\"PLAINTEXT\",\"coordinator\":\"127.0.0.1:25580\"}").getAsJsonObject();
        assertEquals(25580, RuntimeConfiguration.endpoint(config, "coordinator").port());
        for (String endpoint : List.of("localhost:25580", "0.0.0.0:25580", "192.0.2.1:25580")) {
            config.addProperty("coordinator", endpoint); assertThrows(Exception.class, () -> RuntimeConfiguration.endpoint(config, "coordinator"));
        }
        for (String key : List.of("credentialsDirectory", "coordinatorPublicKey", "requiredSuite")) {
            config.addProperty(key, "value");
            var failure = assertThrows(java.io.IOException.class, () -> RuntimeConfiguration.rejectCryptoInPlaintext(config));
            assertTrue(failure.getMessage().contains(key));
            assertTrue(failure.getMessage().contains("remove these fields"));
            assertTrue(failure.getMessage().contains("NOISE_KK"));
            config.remove(key);
        }
        config.addProperty("transportMode", false);
        var invalidMode = assertThrows(IllegalArgumentException.class, () -> RuntimeConfiguration.mode(config));
        assertTrue(invalidMode.getMessage().contains("NOISE_KK or PLAINTEXT"));
    }
}
