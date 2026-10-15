package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointServerCoreWorldIdTest {
    @TempDir
    private Path temporaryDirectory;
    private Config originalConfig;
    private WaypointServerCore server;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        this.server = new WaypointServerCore(this.temporaryDirectory) {
        };
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void readsExistingSignedWorldId() throws Exception {
        Files.writeString(this.temporaryDirectory.resolve("xaeromap.txt"), "id:-1919911982\n");

        this.server.initXearoWorldId(this.temporaryDirectory);

        assertEquals(-1919911982, WaypointServerCore.getWorldId());
    }

    @Test
    void createsWorldIdWhenXaeroMapFileIsMissing() throws Exception {
        Path xaeroMapFile = this.temporaryDirectory.resolve("xaeromap.txt");

        this.server.initXearoWorldId(this.temporaryDirectory);

        assertTrue(Files.isRegularFile(xaeroMapFile));
        String storedId = Files.readString(xaeroMapFile).trim();
        assertTrue(storedId.startsWith("id:"));
        assertEquals(
                Integer.parseInt(storedId.substring("id:".length())),
                WaypointServerCore.getWorldId()
        );
    }
}
