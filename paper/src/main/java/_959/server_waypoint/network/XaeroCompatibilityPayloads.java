package _959.server_waypoint.network;

import java.nio.ByteBuffer;

/** Raw Paper plugin-message envelopes used by XaeroLib and Xaero's map mods. */
public final class XaeroCompatibilityPayloads {
    public static final String XAEROLIB_CHANNEL = "xaerolib:main";
    public static final String XAERO_WORLD_MAP_CHANNEL = "xaeroworldmap:main";
    private static final byte SERVER_HANDSHAKE_PACKET = 0;
    private static final byte DIMENSION_HANDSHAKE_PACKET = 1;
    private static final byte LEVEL_PROPERTIES_PACKET = 0;
    private static final byte XAEROLIB_HANDSHAKE_VERSION = 1;

    private XaeroCompatibilityPayloads() {
    }

    public static byte[] serverHandshake() {
        return new byte[]{SERVER_HANDSHAKE_PACKET, XAEROLIB_HANDSHAKE_VERSION};
    }

    public static byte[] dimensionHandshake() {
        return new byte[]{DIMENSION_HANDSHAKE_PACKET, XAEROLIB_HANDSHAKE_VERSION};
    }

    public static byte[] worldId(int id) {
        return ByteBuffer.allocate(Byte.BYTES + Integer.BYTES)
                .put(LEVEL_PROPERTIES_PACKET)
                .putInt(id)
                .array();
    }
}
