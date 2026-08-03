package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.upload.UploadedWaypointListChunk;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        encodeString(buf, chunk.dimensionName());
        encodeString(buf, chunk.listName());
        List<SimpleWaypoint> waypoints = chunk.waypoints();
        if (waypoints.size() > MAX_WAYPOINTS_PER_LIST_CHUNK) {
            throw new IllegalArgumentException("Too many waypoints in upload chunk");
        }
        buf.writeShort(waypoints.size());
        for (SimpleWaypoint waypoint : waypoints) {
            encodeWaypoint(buf, waypoint);
        }
    }

    static UploadedWaypointListChunk decodeListChunk(ByteBuf buf) {
        String dimensionName = decodeString(buf);
        String listName = decodeString(buf);
        int size = buf.readUnsignedShort();
        if (size > MAX_WAYPOINTS_PER_LIST_CHUNK) {
            throw new IllegalArgumentException("Too many waypoints in upload chunk: " + size);
        }
        List<SimpleWaypoint> waypoints = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            waypoints.add(decodeWaypoint(buf));
        }
        return new UploadedWaypointListChunk(dimensionName, listName, waypoints);
    }

    private static void encodeWaypoint(ByteBuf buf, SimpleWaypoint waypoint) {
        encodeString(buf, waypoint.name());
        encodeString(buf, waypoint.initials());
        WaypointPos pos = waypoint.pos();
        buf.writeInt(pos.x());
        buf.writeInt(pos.y());
        buf.writeInt(pos.z());
        buf.writeInt(waypoint.rgb());
        buf.writeInt(waypoint.yaw());
        buf.writeBoolean(waypoint.global());
    }

    private static SimpleWaypoint decodeWaypoint(ByteBuf buf) {
        String name = decodeString(buf);
        String initials = decodeString(buf);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int rgb = buf.readInt();
        int yaw = buf.readInt();
        boolean global = buf.readBoolean();
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), rgb, yaw, global);
    }
}
