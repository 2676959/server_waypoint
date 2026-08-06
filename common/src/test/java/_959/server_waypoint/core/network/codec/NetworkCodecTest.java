package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WaypointEditRequestBuffer;
import _959.server_waypoint.core.network.buffer.WaypointEditResultBuffer;
import _959.server_waypoint.core.network.buffer.WaypointListUpdateBuffer;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkCodecTest {
    @Test
    void utfStringRoundTripsStringsPastTheOldByteBoundary() {
        String value = "a".repeat(2048);
        ByteBuf buf = Unpooled.buffer();

        UtfStringCodec.encode(buf, value);

        assertEquals(value, UtfStringCodec.decode(buf));
    }

    @Test
    void utfStringTruncatesOverlongStringsWithoutThrowing() {
        String value = "a".repeat(65_536);
        ByteBuf buf = Unpooled.buffer();

        assertDoesNotThrow(() -> UtfStringCodec.encode(buf, value));

        assertEquals("a".repeat(65_535), UtfStringCodec.decode(buf));
    }

    @Test
    void listDecodeIgnoresOversizedCountsWithoutThrowing() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(10_001);

        List<String> decoded = assertDoesNotThrow(() -> ListCodec.decode(buf, ignored -> "item"));

        assertEquals(List.of(), decoded);
    }

    @Test
    void waypointExtraInfoRoundTrips() {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "Home",
                "{\"text\":\"Home\",\"bold\":true}",
                "H",
                new WaypointPos(1, 2, 3),
                0x39C5BB,
                90,
                true,
                List.of("base", "storage"),
                "{\"text\":\"Bring food\",\"color\":\"gold\"}"
        );
        ByteBuf buf = Unpooled.buffer();

        SimpleWaypointCodec.encode(buf, waypoint);
        SimpleWaypoint decoded = SimpleWaypointCodec.decode(buf);

        assertEquals(waypoint.name(), decoded.name());
        assertEquals(waypoint.displayName(), decoded.displayName());
        assertEquals(waypoint.keywords(), decoded.keywords());
        assertEquals(waypoint.description(), decoded.description());
    }

    @Test
    void waypointListDisplayNameRoundTrips() {
        WaypointList waypointList = new WaypointList(
                "Bases",
                "{\"text\":\"Bases\",\"color\":\"aqua\"}",
                4,
                List.of()
        );
        ByteBuf buf = Unpooled.buffer();

        WaypointListCodec.encode(buf, waypointList);
        WaypointList decoded = WaypointListCodec.decode(buf);

        assertEquals(waypointList.name(), decoded.name());
        assertEquals(waypointList.displayName(), decoded.displayName());
    }

    @Test
    void listDisplayNameRoundTripsInIncrementalModification() {
        String displayName = "{\"text\":\"Bases\",\"color\":\"aqua\"}";
        WaypointModificationBuffer modification = new WaypointModificationBuffer(
                "minecraft:overworld",
                "Bases",
                displayName,
                null,
                null,
                WaypointModificationType.ADD_LIST,
                WaypointList.SERVER_N
        );
        ByteBuf buf = Unpooled.buffer();

        WaypointModificationBufferCodec.encode(buf, modification);
        WaypointModificationBuffer decoded = WaypointModificationBufferCodec.decode(buf);

        assertEquals("Bases", decoded.listName());
        assertEquals(displayName, decoded.listDisplayName());
    }

    @Test
    void waypointEditRequestAndResultRoundTrip() {
        WaypointPatch patch = new WaypointPatch(
                PatchField.set(""),
                PatchField.set(""),
                PatchField.set("I"),
                PatchField.set(new WaypointPos(4, 5, 6)),
                PatchField.set(0x123456),
                PatchField.set(90),
                PatchField.set(false),
                PatchField.clear(),
                PatchField.clear()
        );
        WaypointEditRequestBuffer request = new WaypointEditRequestBuffer(
                42L, "minecraft:overworld", "", "way point", 7, patch
        );
        ByteBuf requestBuf = Unpooled.buffer();
        WaypointEditRequestBufferCodec.encode(requestBuf, request);
        WaypointEditRequestBuffer decodedRequest = WaypointEditRequestBufferCodec.decode(requestBuf);
        assertEquals(request, decodedRequest);
        assertFalse(decodedRequest.patch().visibility().requiredValue());

        SimpleWaypoint canonical = new SimpleWaypoint(
                "", "", "I", new WaypointPos(4, 5, 6), 0x123456, 90, false,
                List.of(), ""
        );
        WaypointEditResultBuffer result = new WaypointEditResultBuffer(
                42L, EditResultStatus.SUCCESS, "minecraft:overworld", "", "way point",
                canonical, 8
        );
        ByteBuf resultBuf = Unpooled.buffer();
        WaypointEditResultBufferCodec.encode(resultBuf, result);
        WaypointEditResultBuffer decodedResult = WaypointEditResultBufferCodec.decode(resultBuf);
        assertEquals(result.requestId(), decodedResult.requestId());
        assertEquals(result.status(), decodedResult.status());
        assertEquals("", decodedResult.waypoint().name());
        assertEquals(8, decodedResult.listRevision());
    }

    @Test
    void waypointListUpdateRoundTripsPreviousIdentifierAndCanonicalSnapshot() {
        WaypointListUpdateBuffer update = new WaypointListUpdateBuffer(
                "minecraft:overworld",
                "old list",
                new WaypointList("", "{\"text\":\"Shown\"}", 9, List.of())
        );
        ByteBuf buf = Unpooled.buffer();

        WaypointListUpdateBufferCodec.encode(buf, update);
        WaypointListUpdateBuffer decoded = WaypointListUpdateBufferCodec.decode(buf);

        assertEquals("old list", decoded.previousListIdentifier());
        assertEquals("", decoded.waypointList().name());
        assertEquals("{\"text\":\"Shown\"}", decoded.waypointList().displayName());
        assertEquals(9, decoded.waypointList().getSyncNum());
    }
}
