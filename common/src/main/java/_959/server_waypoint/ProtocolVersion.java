package _959.server_waypoint;

public final class ProtocolVersion {
    // Protocol 3 adds explicit server collection scopes. Public 4.0.x releases used protocol 2.
    // Release 3.0.4 sent handshake version 0 and Forge/NeoForge channel version "1";
    // reusing either lets a 3.0.4 peer pass the version check.
    public static final int PROTOCOL_VERSION = 3;
    // For the Forge and NeoForge channel APIs that take the version as a string.
    // No feature release version is assigned yet; compatibility feedback reports this protocol.
    public static final String PROTOCOL_VERSION_STRING = Integer.toString(PROTOCOL_VERSION);
}
