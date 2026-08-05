package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class UploadCodecSupport {
    static final int MAX_STRING_BYTES = 1_024;
    static final int MAX_DIMENSIONS = 256;
    static final int MAX_LISTS_PER_CHUNK = 4;
    static final int MAX_WAYPOINTS_PER_LIST_CHUNK = 64;

    private UploadCodecSupport() {
    }

    static void encodeString(ByteBuf buf, String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String exceeds " + MAX_STRING_BYTES + " UTF-8 bytes");
        }
        buf.writeShort(raw.length);
        buf.writeBytes(raw);
    }

    static String decodeString(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        if (length > MAX_STRING_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid upload string length: " + length);
        }
        byte[] raw = new byte[length];
        buf.readBytes(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }

    static void encodeOptionalString(ByteBuf buf, String value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            encodeString(buf, value);
        }
    }

    static String decodeOptionalString(ByteBuf buf) {
        return buf.readBoolean() ? decodeString(buf) : null;
    }

    static void encodeListChunk(ByteBuf buf, UploadedWaypointListChunk chunk) {
        if (chunk.waypoints().size() > MAX_WAYPOINTS_PER_LIST_CHUNK) {
            throw new IllegalArgumentException("Too many waypoints in upload chunk");
        }
        WaypointList waypointList = new WaypointList(
                chunk.listName(),
                chunk.listName(),
                WaypointList.SERVER_N,
                chunk.waypoints()
        );
        WaypointListBufferCodec.encode(buf, new WaypointListBuffer(chunk.dimensionName(), waypointList));
    }

    static UploadedWaypointListChunk decodeListChunk(ByteBuf buf) {
        WaypointListBuffer waypointListBuffer = WaypointListBufferCodec.decode(buf);
        WaypointList waypointList = waypointListBuffer.waypointList();
        if (waypointList.size() > MAX_WAYPOINTS_PER_LIST_CHUNK) {
            throw new IllegalArgumentException("Too many waypoints in upload chunk: " + waypointList.size());
        }
        return new UploadedWaypointListChunk(
                waypointListBuffer.dimensionName(),
                waypointList.name(),
                waypointList.simpleWaypoints()
        );
    }
}
