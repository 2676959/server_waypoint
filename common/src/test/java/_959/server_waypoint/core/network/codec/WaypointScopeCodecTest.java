package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.*;
import _959.server_waypoint.core.network.data.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WaypointScopeCodecTest {
    @Test
    void everyWaypointMessageRetainsItsScope() {
        assertEquals(3, ProtocolVersion.PROTOCOL_VERSION);
        assertEquals("3", ProtocolVersion.PROTOCOL_VERSION_STRING);
        for (WaypointScope scope : WaypointScope.values()) {
            var list = WaypointList.build("Homes", 7);
            List<ChunkedMessage> messages = List.of(
                    WaypointData.world(scope, List.of(new DimensionWaypointData("overworld", List.of(list)))),
                    WaypointData.updates(scope, List.of(new DimensionWaypointData("gone", List.of()))),
                    new WaypointListUpdateMessage(scope, "overworld", "Old", list),
                    new WaypointModificationMessage(scope, "overworld", "Homes", "Homes", "Base", null,
                            WaypointModificationType.REMOVE, 8),
                    new WaypointEditRequestMessage(scope, 1, "overworld", "Homes", "Base", 7, WaypointPatch.empty()),
                    new WaypointEditResultMessage(scope, 1, EditResultStatus.PERMISSION_DENIED,
                            "overworld", "Homes", "Base", null, 7),
                    new ClientUpdateRequestMessage(scope, List.of()),
                    new WaypointCollectionStateMessage(scope, false));
            for (ChunkedMessage message : messages) {
                var buffer = Unpooled.buffer();
                try {
                    ChunkedMessageRegistry.encode(buffer, message, new EncodingContext(1_000_000));
                    var decoded = ChunkedMessageRegistry.decode(message.getType().id(), buffer,
                            new DecodingContext(1_000_000, 10_000));
                    assertEquals(message.getClass(), decoded.getClass());
                    assertEquals(scope, decoded.getClass().getMethod("scope").invoke(decoded));
                    assertEquals(0, buffer.readableBytes());
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                } finally { buffer.release(); }
            }
        }
    }

    @Test
    void invalidScopeAndPrivateUploadAreRejected() {
        var invalid = Unpooled.buffer().writeByte(255);
        try {
            assertThrows(IllegalArgumentException.class, () -> WaypointDataCodec.decode(invalid,
                    new DecodingContext(1_000_000, 10_000)));
        } finally { invalid.release(); }
        var privateUpload = Unpooled.buffer().writeByte(WaypointScope.PRIVATE.ordinal()).writeByte(WaypointData.Type.UPLOAD.ordinal());
        try {
            assertThrows(IllegalArgumentException.class, () -> WaypointDataCodec.decode(privateUpload,
                    new DecodingContext(1_000_000, 10_000)));
        } finally { privateUpload.release(); }
        assertThrows(IllegalArgumentException.class, () -> WaypointData.upload(WaypointScope.PRIVATE,
                UUID.randomUUID(), UploadStatus.SUCCESS, List.of()));
    }
}
