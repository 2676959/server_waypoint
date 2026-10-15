package _959.server_waypoint.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XaeroCompatibilityPayloadsTest {
    @Test
    void encodesXaeroLibHandshakes() {
        assertArrayEquals(new byte[]{0, 1}, XaeroCompatibilityPayloads.serverHandshake());
        assertArrayEquals(new byte[]{1, 1}, XaeroCompatibilityPayloads.dimensionHandshake());
    }

    @Test
    void encodesSignedXaeroMapWorldId() {
        assertArrayEquals(
                new byte[]{0, -115, -112, 119, -46},
                XaeroCompatibilityPayloads.worldId(-1919911982)
        );
    }

    @Test
    void usesXaeroWorldMapMainChannel() {
        assertEquals("xaeroworldmap:main", XaeroCompatibilityPayloads.XAERO_WORLD_MAP_CHANNEL);
    }
}
