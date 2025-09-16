package _959.server_waypoint.common.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ServerWaypointClientMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
    private static boolean handshakeFinished = false;

    public static boolean isHandshakeFinished() {
        return handshakeFinished;
    }

    public static void setHandshakeFinished(boolean handshakeFinished) {
        ServerWaypointClientMod.handshakeFinished = handshakeFinished;
    }
}
