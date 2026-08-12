package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.network.message.WaypointListUpdateMessage;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkCodecTest {
    private static final int BYTE_BUDGET = 2 * 1_024 * 1_024;
    private static final int OBJECT_BUDGET = 100_000;

    @Test
    void serverHandshakeCarriesChunkCompressionSetting() {
        ServerHandshakeBuffer handshake = new ServerHandshakeBuffer(99, 42, false);
        ByteBuf buffer = Unpooled.buffer();

        ServerHandshakeCodec.encode(buffer, handshake);
        ServerHandshakeBuffer decoded = ServerHandshakeCodec.decode(buffer);

        assertEquals(99, decoded.version());
        assertEquals(42, decoded.serverId());
        assertFalse(decoded.compressChunkedMessages());
    }

    @Test
    void clientHandshakeCarriesItsDeclaredProtocolVersion() {
        ClientHandshakeBuffer handshake = new ClientHandshakeBuffer(98);
        ByteBuf buffer = Unpooled.buffer();

        ClientHandshakeCodec.encode(buffer, handshake);

        assertEquals(handshake, ClientHandshakeCodec.decode(buffer));
    }

    @Test
    void utfStringAboveUnsignedShortBoundaryRoundTripsWithoutTruncation() {
        String value = "\u00e9".repeat(40_000);
        ByteBuf buffer = Unpooled.buffer();

        UtfStringCodec.encode(buffer, value, encoding());

        assertEquals(80_000, buffer.getInt(0));
        assertEquals(value, UtfStringCodec.decode(buffer, decoding()));
    }

    @Test
    void utfStringRejectsNegativeAndUnavailableLengths() {
        ByteBuf negative = Unpooled.buffer().writeInt(-1);
        ByteBuf unavailable = Unpooled.buffer().writeInt(10).writeByte(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> UtfStringCodec.decode(negative, decoding())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> UtfStringCodec.decode(unavailable, decoding())
        );
    }

    @Test
    void utfStringEncodingHonorsEnclosingByteBudget() {
        ByteBuf buffer = Unpooled.buffer();

        assertThrows(
                MessageEncodingException.class,
                () -> UtfStringCodec.encode(buffer, "abcdef", new EncodingContext(5))
        );
    }

    @Test
    void listAboveOldItemCeilingRoundTripsWithinBudget() {
        List<Integer> values = IntStream.range(0, 12_000).boxed().toList();
        ByteBuf buffer = Unpooled.buffer();

        ListCodec.encode(
                buffer,
                values,
                (target, value, ignored) -> target.writeInt(value),
                encoding()
        );
        List<Integer> decoded = ListCodec.decode(
                buffer,
                (source, ignored) -> source.readInt(),
                decoding()
        );

        assertEquals(values, decoded);
    }

    @Test
    void listRejectsNegativeAndHugeDeclaredCountsWithoutLargePreallocation() {
        ByteBuf negative = Unpooled.buffer().writeInt(-1);
        ByteBuf huge = Unpooled.buffer().writeInt(Integer.MAX_VALUE);

        assertThrows(
                IllegalArgumentException.class,
                () -> ListCodec.decode(negative, (source, ignored) -> source.readByte(), decoding())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ListCodec.decode(huge, (source, ignored) -> source.readByte(), decoding())
        );
    }

    @Test
    void listRejectsZeroByteElementDecoderAndObjectBudgetExhaustion() {
        ByteBuf zeroByte = Unpooled.buffer().writeInt(1);
        ByteBuf tooManyObjects = Unpooled.buffer()
                .writeInt(3)
                .writeInt(1)
                .writeInt(2)
                .writeInt(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> ListCodec.decode(zeroByte, (source, ignored) -> "item", decoding())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ListCodec.decode(
                        tooManyObjects,
                        (source, ignored) -> source.readInt(),
                        new DecodingContext(BYTE_BUDGET, 2)
                )
        );
    }

    @Test
    void waypointStructureUsesOneCanonicalCodec() {
        SimpleWaypoint waypoint = waypoint("Home");
        WaypointList waypointList = new WaypointList(
                "Bases",
                "{\"text\":\"Bases\",\"color\":\"aqua\"}",
                4,
                List.of(waypoint)
        );
        ByteBuf buffer = Unpooled.buffer();

        WaypointListCodec.encode(buffer, waypointList, encoding());
        WaypointList decoded = WaypointListCodec.decode(buffer, decoding());

        assertEquals(waypointList.name(), decoded.name());
        assertEquals(waypointList.displayName(), decoded.displayName());
        assertEquals(waypoint.displayName(), decoded.simpleWaypoints().get(0).displayName());
        assertEquals(waypoint.keywords(), decoded.simpleWaypoints().get(0).keywords());
        assertEquals(waypoint.description(), decoded.simpleWaypoints().get(0).description());
    }

    @Test
    void logicalMessageCodecsRoundTripCanonicalSnapshots() {
        WaypointModificationMessage modification = new WaypointModificationMessage(
                "minecraft:overworld",
                "Bases",
                "{\"text\":\"Bases\",\"color\":\"aqua\"}",
                null,
                null,
                WaypointModificationType.ADD_LIST,
                WaypointList.SERVER_N
        );
        ByteBuf modificationBuffer = Unpooled.buffer();
        WaypointModificationMessageCodec.encode(modificationBuffer, modification, encoding());
        assertEquals(
                modification,
                WaypointModificationMessageCodec.decode(modificationBuffer, decoding())
        );

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
        WaypointEditRequestMessage request = new WaypointEditRequestMessage(
                42L,
                "minecraft:overworld",
                "",
                "way point",
                7,
                patch
        );
        ByteBuf requestBuffer = Unpooled.buffer();
        WaypointEditRequestMessageCodec.encode(requestBuffer, request, encoding());
        assertEquals(request, WaypointEditRequestMessageCodec.decode(requestBuffer, decoding()));

        WaypointEditResultMessage result = new WaypointEditResultMessage(
                42L,
                EditResultStatus.DUPLICATE_KEYWORD,
                "minecraft:overworld",
                "",
                "way point",
                waypoint(""),
                8
        );
        ByteBuf resultBuffer = Unpooled.buffer();
        WaypointEditResultMessageCodec.encode(resultBuffer, result, encoding());
        WaypointEditResultMessage decodedResult =
                WaypointEditResultMessageCodec.decode(resultBuffer, decoding());
        assertEquals(result.status(), decodedResult.status());
        assertEquals(8, decodedResult.listRevision());

        WaypointListUpdateMessage update = new WaypointListUpdateMessage(
                "minecraft:overworld",
                "old list",
                new WaypointList("", "{\"text\":\"Shown\"}", 9, List.of())
        );
        ByteBuf updateBuffer = Unpooled.buffer();
        WaypointListUpdateMessageCodec.encode(updateBuffer, update, encoding());
        WaypointListUpdateMessage decodedUpdate =
                WaypointListUpdateMessageCodec.decode(updateBuffer, decoding());
        assertEquals("old list", decodedUpdate.previousListIdentifier());
        assertEquals(9, decodedUpdate.waypointList().getSyncNum());
    }

    @Test
    void waypointDataAndUploadRequestRoundTrip() {
        List<SimpleWaypoint> waypoints = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            waypoints.add(waypoint("Waypoint " + index));
        }
        WaypointData uploadData = WaypointData.upload(
                UUID.randomUUID(),
                UploadStatus.SUCCESS,
                List.of(new DimensionWaypointData(
                        "minecraft:overworld",
                        List.of(new WaypointList("Bases", WaypointList.SERVER_N, waypoints))
                ))
        );
        ByteBuf dataBuffer = Unpooled.buffer();
        WaypointDataCodec.encode(dataBuffer, uploadData, encoding());
        WaypointData decodedData = WaypointDataCodec.decode(dataBuffer, decoding());
        assertEquals(uploadData.uploadData().requestId(), decodedData.uploadData().requestId());
        assertEquals(65, decodedData.dimensions().get(0).waypointLists().get(0).size());

        UploadRequestBuffer request = new UploadRequestBuffer(
                UUID.randomUUID(),
                List.of("minecraft:overworld", "minecraft:the_nether"),
                "Bases",
                null
        );
        ByteBuf requestBuffer = Unpooled.buffer();
        UploadRequestCodec.encode(requestBuffer, request, encoding());
        assertEquals(request, UploadRequestCodec.decode(requestBuffer, decoding()));
    }

    private static SimpleWaypoint waypoint(String name) {
        return new SimpleWaypoint(
                name,
                "{\"text\":\"Home\",\"bold\":true}",
                "H",
                new WaypointPos(1, 2, 3),
                0x39C5BB,
                90,
                true,
                List.of("base", "storage"),
                "{\"text\":\"Bring food\",\"color\":\"gold\"}"
        );
    }

    private static EncodingContext encoding() {
        return new EncodingContext(BYTE_BUDGET);
    }

    private static DecodingContext decoding() {
        return new DecodingContext(BYTE_BUDGET, OBJECT_BUDGET);
    }
}
