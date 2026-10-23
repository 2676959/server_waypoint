package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.collection.WaypointCollectionKey;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                        WaypointCollectionKey.shared(),
                        "minecraft:the_nether",
                        "portals",
                        "portals",
                        "Hub",
                        "Hub",
                        "",
                        List.of(),
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
                        WaypointCollectionKey.shared(),
                        "minecraft:overworld",
                        "towns",
                        "towns",
                        "Village",
                        "Village",
                        "",
                        List.of(),
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
    void privateIdentityRoundTripsAndFormerFormatIsRejected() {
        UUID owner = UUID.randomUUID();
        NavigationSession session = new NavigationSession(owner,
                new NavigationTarget(WaypointCollectionKey.privateFor(owner), "minecraft:overworld", "Homes", "Homes",
                        "Base", "Base", "", List.of(), new WaypointPos(1, 64, 2), 0x123456),
                Set.of(NavigationMethod.ACTIONBAR), TextDisplayTransformation.defaultValue());
        String encoded = NavigationSessionCodec.encode(session);
        assertEquals(WaypointCollectionKey.privateFor(owner), NavigationSessionCodec.decode(encoded).orElseThrow().collectionKey());
        assertTrue(NavigationSessionCodec.decode(encoded.replace("\"version\":3", "\"version\":2")).isEmpty());
        assertTrue(NavigationSessionCodec.decode(encoded.replace("PRIVATE", "LOCAL")).isEmpty());
        assertTrue(NavigationSessionCodec.decode(encoded.replace(owner.toString(), "invalid-uuid")).isEmpty());
        assertTrue(NavigationSessionCodec.decode(encoded.replace("PRIVATE", "SHARED")).isEmpty());
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
                        {"version":3,"scope":"SHARED","dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":["unknown"],"transformation":{"translation":{"x":0,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":3,"scope":"SHARED","dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[]}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":3,"scope":"SHARED","dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[],"transformation":{"translation":{"x":17,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
        assertEquals(
                Optional.empty(),
                NavigationSessionCodec.decode("""
                        {"version":4,"scope":"SHARED","dimension":"minecraft:overworld","list":"towns","waypoint":"Village","methods":[],"transformation":{"translation":{"x":0,"y":0,"z":0},"rotation":{"x":0,"y":0,"z":0},"scale":{"x":1,"y":1,"z":1}}}
                        """)
        );
    }
}
