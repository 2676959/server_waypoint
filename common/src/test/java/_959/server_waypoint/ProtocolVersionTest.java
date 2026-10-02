package _959.server_waypoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A version a 3.0.4 peer also sends lets it pass the version check while its packets mean something else. */
class ProtocolVersionTest {
    @Test
    void handshakeVersionDiffersFromRelease304() {
        assertNotEquals(0, ProtocolVersion.PROTOCOL_VERSION, "release 3.0.4 sent handshake version 0");
    }

    @Test
    void forgeAndNeoForgeChannelVersionDiffersFromRelease304() {
        assertNotEquals("1", ProtocolVersion.PROTOCOL_VERSION_STRING,
                "release 3.0.4 declared Forge and NeoForge channel version \"1\"");
    }
}
