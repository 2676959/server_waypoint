package _959.server_waypoint.server;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointServerPluginWorldIdTest {
    @TempDir
    private Path temporaryDirectory;
    private Config originalConfig;

    @BeforeEach
    void rememberConfig() {
        this.originalConfig = WaypointServerCore.CONFIG;
    }

    @AfterEach
    void restoreConfig() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void usesLoadedServerIdAsXaeroWorldId() {
        WaypointServerPlugin server = new WaypointServerPlugin(this.temporaryDirectory);
        server.loadConfig(new StringReader("""
                {
                    "serverId": -1919911982,
                    "Features": {
                        "sendXaerosWorldId": true
                    }
                }
                """));

        server.initializeXaeroWorldId();

        assertEquals(-1919911982, WaypointServerCore.getWorldId());
    }
}
