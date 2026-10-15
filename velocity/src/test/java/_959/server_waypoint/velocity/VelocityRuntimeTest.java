package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.transport.BackendPresence;
import _959.server_waypoint.crossserver.transport.TransportResult;
import _959.server_waypoint.crossserver.transport.ConnectionMetrics;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class VelocityRuntimeTest {
    @TempDir Path directory;

    @Test void statusCommandReportsDisabledAndLiveCoordinatorWithoutReadingDiskAgain() throws Exception {
        var runtime = new VelocityRuntime(null, directory);
        try {
            assertEquals(java.util.List.of("Server Waypoint cross-server: starting", "Transport mode: inactive",
                            "Online servers (0): none", "Offline servers (0): none"),
                    CrossServerStatusCommand.lines(runtime.status()));
            assertEquals(TransportResult.DISABLED, runtime.start().toCompletableFuture().get());
            assertEquals("Server Waypoint cross-server: disabled", CrossServerStatusCommand.lines(runtime.status()).get(0));
        } finally {
            runtime.stop().toCompletableFuture().get();
        }
        var creative = new RemoteServerId("creative");
        var survival = new RemoteServerId("survival");
        var coordinator = new CoordinatorAgent.Status(true, 25580, Map.of(
                survival, new BackendPresence(survival, TransportMode.NOISE_KK, Set.of(), 2, Instant.EPOCH),
                new RemoteServerId("lobby"), new BackendPresence(new RemoteServerId("lobby"),
                        TransportMode.NOISE_KK, Set.of(), 1, Instant.EPOCH)),
                new ConnectionMetrics.Snapshot(0, 0, 0, 0, 0, 0));
        assertEquals(java.util.List.of("Server Waypoint cross-server: running",
                        "Transport mode: NOISE_KK (encrypted)", "Coordinator listening on port 25580",
                        "Online servers (1): survival", "Offline servers (1): creative"),
                CrossServerStatusCommand.lines(new VelocityRuntime.Status(false, TransportResult.SUCCESS,
                        TransportMode.NOISE_KK, Set.of(creative, survival), coordinator, null)));
        assertEquals(java.util.List.of("Server Waypoint cross-server: unavailable",
                        "Transport mode: PLAINTEXT (unencrypted)",
                        "Online servers (0): none", "Offline servers (2): creative, survival"),
                CrossServerStatusCommand.lines(new VelocityRuntime.Status(false, TransportResult.SUCCESS,
                        TransportMode.PLAINTEXT, Set.of(creative, survival),
                        new CoordinatorAgent.Status(false, -1, Map.of(), coordinator.metrics()), null)));
    }

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
            assertTrue(CrossServerStatusCommand.lines(runtime.status()).get(0).contains("invalid configuration"));
            assertTrue(CrossServerStatusCommand.lines(runtime.status()).contains("Transport mode: PLAINTEXT (unencrypted)"));
        } finally {
            runtime.stop().toCompletableFuture().get();
        }
    }
}
