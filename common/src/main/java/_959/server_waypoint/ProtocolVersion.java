package _959.server_waypoint;

public final class ProtocolVersion {
    // Public 4.1.x releases. Protocol 3 adds collection scopes for private waypoints and a distinct waypoint
    // restore delta; 4.0.x releases sent 2. Unreleased development builds also used 3 and 4.
    // Release 3.0.4 sent handshake version 0 and Forge/NeoForge channel version "1";
    // reusing either lets a 3.0.4 peer pass the version check.
    public static final int PROTOCOL_VERSION = 3;
    // For the Forge and NeoForge channel APIs that take the version as a string.
    public static final String PROTOCOL_VERSION_STRING = Integer.toString(PROTOCOL_VERSION);
    public static final String COMPATIBLE_VERSION = "4.1.x";
}
