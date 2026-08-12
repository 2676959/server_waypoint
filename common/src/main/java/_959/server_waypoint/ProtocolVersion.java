package _959.server_waypoint;

public final class ProtocolVersion {
    // Waypoint editing and upload add custom payloads, so both the client and
    // server must use the same protocol revision.
    public static final int PROTOCOL_VERSION = 5;
    public static final String COMPATIBLE_VERSION = "3.1.x";
}
