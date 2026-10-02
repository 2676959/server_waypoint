package _959.server_waypoint.crossserver;

/**
 * Version 1 contract shared by backends and the coordinator.
 * See docs/features/cross-server/specs/cross-server-protocol-v1.md. This does not register runtime features.
 */
public final class CrossServerProtocol {
    /** Independent of the Minecraft custom-payload ProtocolVersion. */
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_SERVER_ID_LENGTH = 64;
    public static final String SERVER_ID_PATTERN = "[a-z0-9][a-z0-9_-]{0,63}";

    public static final String REMOTE_LIST_PERMISSION = "server_waypoint.command.remote.list";
    public static final int REMOTE_LIST_DEFAULT_LEVEL = 0;
    public static final String REMOTE_TP_PERMISSION = "server_waypoint.command.remote.tp";
    public static final int REMOTE_TP_DEFAULT_LEVEL = 2;

    private CrossServerProtocol() {
    }
}
