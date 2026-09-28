package _959.server_waypoint.common.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointItemAlphaTest {
    @Test
    void worldItemTintUsesConfiguredAlphaAndIsLimitedToItsDrawScope() {
        assertEquals(-1, WaypointItemAlpha.currentTint());

        int previous = WaypointItemAlpha.pushWorldItemTint(128);
        try {
            assertEquals(0x80808080, WaypointItemAlpha.currentTint());
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }

        assertEquals(-1, WaypointItemAlpha.currentTint());
    }

    @Test
    void configuredAlphaCoversFullyTransparentAndOpaqueItems() {
        int previous = WaypointItemAlpha.pushWorldItemTint(0);
        try {
            assertEquals(0x00000000, WaypointItemAlpha.currentTint());
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }

        previous = WaypointItemAlpha.pushWorldItemTint(255);
        try {
            assertEquals(0xFFFFFFFF, WaypointItemAlpha.currentTint());
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }
    }
}
