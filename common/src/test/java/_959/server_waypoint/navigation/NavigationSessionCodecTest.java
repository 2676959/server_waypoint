package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationSessionCodecTest {
    @Test
    void roundTripPreservesWaypointIdentityAndMethodIds() {
        NavigationSession session = new NavigationSession(
                UUID.randomUUID(),
                new NavigationTarget(
                        "minecraft:the_nether",
                        "portals",
                        "Hub",
                        new WaypointPos(10, 64, -20),
                        0x123456
                ),
                Set.of(NavigationMethod.COMPASS, NavigationMethod.ACTIONBAR)
        );

        StoredNavigationSession decoded = NavigationSessionCodec.decode(
                NavigationSessionCodec.encode(session)
        ).orElseThrow();

        assertEquals("minecraft:the_nether", decoded.dimensionName());
        assertEquals("portals", decoded.listName());
        assertEquals("Hub", decoded.waypointName());
        assertEquals(
                Set.of(NavigationMethod.COMPASS, NavigationMethod.ACTIONBAR),
                decoded.enabledMethods()
        );
    }

    @Test
    void emptyMethodSelectionRoundTrips() {
        NavigationSession session = new NavigationSession(
                UUID.randomUUID(),
                new NavigationTarget(
                        "minecraft:overworld",
                        "towns",
                        "Village",
                        new WaypointPos(1, 2, 3),
                        0x39C5BB
                ),
                Set.of()
        );

        StoredNavigationSession decoded = NavigationSessionCodec.decode(
                NavigationSessionCodec.encode(session)
        ).orElseThrow();

        assertTrue(decoded.enabledMethods().isEmpty());
    }

    @Test
    void malformedUnknownAndFuturePayloadsAreRejected() {
        assertEquals(Optional.empty(), NavigationSessionCodec.decode("not-json"));
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":1,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":["unknown"]}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":2,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[]}
                        """)
        );
    }
}
