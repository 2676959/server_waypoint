package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
import io.netty.buffer.ByteBuf;
import _959.server_waypoint.core.collection.WaypointScope;

import java.util.UUID;
import java.util.List;

public final class WaypointDataCodec {
    private WaypointDataCodec() {
    }

    public static void encode(ByteBuf buf, WaypointData waypointData, EncodingContext context) {
        WaypointScopeCodec.encode(buf, waypointData.scope());
        buf.writeByte(waypointData.type().ordinal());
        switch (waypointData.type()) {
            case UPDATES, WORLD ->
                    DimensionWaypointsListCodec.encode(buf, waypointData.dimensions(), context);
            case DIMENSION, WAYPOINT_LIST, WAYPOINT ->
                    DimensionWaypointCodec.encode(buf, waypointData.singleDimension(), context);
            case UPLOAD -> encodeUpload(buf, waypointData, context);
        }
    }

    public static WaypointData decode(ByteBuf buf, DecodingContext context) {
        WaypointScope scope = WaypointScopeCodec.decode(buf);
        int typeId = buf.readUnsignedByte();
        WaypointData.Type[] types = WaypointData.Type.values();
        if (typeId >= types.length) {
            throw new IllegalArgumentException("Invalid waypoint-data type: " + typeId);
        }
        return switch (types[typeId]) {
            case UPDATES -> WaypointData.updates(scope, DimensionWaypointsListCodec.decode(buf, context));
            case DIMENSION -> WaypointData.dimension(scope, DimensionWaypointCodec.decode(buf, context));
            case WAYPOINT_LIST -> {
                DimensionWaypointData dimension = DimensionWaypointCodec.decode(buf, context);
                if (dimension.waypointLists().size() != 1) {
                    throw new IllegalArgumentException("Waypoint-list data must contain exactly one list");
                }
                yield WaypointData.waypointList(scope, dimension.dimensionName(), dimension.waypointLists().get(0));
            }
            case WORLD -> WaypointData.world(scope, DimensionWaypointsListCodec.decode(buf, context));
            case UPLOAD -> {
                if (scope != WaypointScope.SHARED) throw new IllegalArgumentException("Private uploads are forbidden");
                yield decodeUpload(buf, context);
            }
            case WAYPOINT -> new WaypointData(
                    scope, WaypointData.Type.WAYPOINT, List.of(DimensionWaypointCodec.decode(buf, context)), null
            );
        };
    }

    private static void encodeUpload(ByteBuf buf, WaypointData waypointData, EncodingContext context) {
        WaypointData.Upload upload = waypointData.uploadData();
        buf.writeLong(upload.requestId().getMostSignificantBits());
        buf.writeLong(upload.requestId().getLeastSignificantBits());
        buf.writeByte(upload.status().ordinal());
        DimensionWaypointsListCodec.encode(buf, waypointData.dimensions(), context);
    }

    private static WaypointData decodeUpload(ByteBuf buf, DecodingContext context) {
        UUID requestId = new UUID(buf.readLong(), buf.readLong());
        int statusId = buf.readUnsignedByte();
        UploadStatus[] statuses = UploadStatus.values();
        if (statusId >= statuses.length) {
            throw new IllegalArgumentException("Invalid upload status: " + statusId);
        }
        return WaypointData.upload(
                WaypointScope.SHARED,
                requestId,
                statuses[statusId],
                DimensionWaypointsListCodec.decode(buf, context)
        );
    }
}
