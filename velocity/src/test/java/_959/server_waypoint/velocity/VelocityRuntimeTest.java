package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.transport.TransportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VelocityRuntimeTest {
    @TempDir Path directory;

    @Test void startupNamesUnknownBackendEntriesAndSuggestsFix() throws Exception {
        Files.writeString(directory.resolve("cross-server.json"), """
                {
                    "enabled": true,
                    "transportMode": "PLAINTEXT",
                    "backends": {
                        "lobby": {
                            "enabled": false,
                            "wrongName": true,
                            "otherName": false
                        }
                    }
                }
                """);
        var runtime = new VelocityRuntime(null, directory);
        try {
            assertEquals(TransportResult.INVALID_CONFIGURATION, runtime.start().toCompletableFuture().get());
            assertTrue(runtime.startupFailureDetails().contains("backends.lobby"));
            assertTrue(runtime.startupFailureDetails().contains("otherName, wrongName"));
            assertTrue(runtime.startupFailureDetails().contains("remove them or correct their names"));
        } finally {
            runtime.stop().toCompletableFuture().get();
        }
    }
}
