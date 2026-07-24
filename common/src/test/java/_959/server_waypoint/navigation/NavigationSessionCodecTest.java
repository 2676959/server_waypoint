package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationSessionCodecTest {
    @Test
    void roundTripPreservesWaypointIdentityMethodsAndTransformation() {
        TextDisplayTransformation transformation = new TextDisplayTransformation(
                new Vector3f(1.0F, 2.0F, 3.0F),
                new Vector3f(10.0F, 20.0F, 30.0F),
                new Vector3f(1.0F, 2.0F, 1.0F)
        );
        NavigationSession session = new NavigationSession(
                UUID.randomUUID(),
                new NavigationTarget(
                        "minecraft:the_nether",
                        "portals",
                        "Hub",
                        new WaypointPos(10, 64, -20),
                        0x123456
                ),
                Set.of(NavigationMethod.COMPASS, NavigationMethod.ACTIONBAR),
                transformation
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
        assertEquals(transformation, decoded.textDisplayTransformation());
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
                Set.of(),
                TextDisplayTransformation.defaultValue()
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
                        {"version":1,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[]}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":2,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":["unknown"],"transformation":{"translation":{"x":0,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":2,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[]}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":2,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[],"transformation":{"translation":{"x":17,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":3,"dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[],"transformation":{"translation":{"x":0,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
    }
}
