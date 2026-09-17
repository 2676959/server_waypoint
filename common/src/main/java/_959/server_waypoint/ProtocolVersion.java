package _959.server_waypoint;

public final class ProtocolVersion {
    // Upload requests include an explicit map-mod target; protocol 9 candidates lack this byte.
    public static final int PROTOCOL_VERSION = 10;
    public static final String COMPATIBLE_VERSION = "3.1.x";
}
