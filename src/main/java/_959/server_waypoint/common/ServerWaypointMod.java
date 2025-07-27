package _959.server_waypoint.common;

import _959.server_waypoint.common.server.WaypointServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class ServerWaypointMod implements IPlatformConfigPath {
    public static final String MOD_ID = "server_waypoint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    protected void initServer() {
        LOGGER.info("Initialize waypoint server.");
        WaypointServer waypointServer = new WaypointServer(getConfigDirectory());
        try {
            waypointServer.initServer();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize server waypoint", e);
            throw new RuntimeException(e);
        }
    }
}
