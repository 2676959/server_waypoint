package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public final class WaypointDataCodec {
    private WaypointDataCodec() {
    }

    public static void encode(ByteBuf buf, WaypointData waypointData, EncodingContext context) {
        buf.writeByte(waypointData.type().ordinal());
        switch (waypointData.type()) {
            case UPDATES, WORLD ->
                    DimensionWaypointsListCodec.encode(buf, waypointData.dimensions(), context);
            case DIMENSION, WAYPOINT_LIST ->
                    DimensionWaypointCodec.encode(buf, waypointData.singleDimension(), context);
            case UPLOAD -> encodeUpload(buf, waypointData, context);
        }
    }

    public static WaypointData decode(ByteBuf buf, DecodingContext context) {
        int typeId = buf.readUnsignedByte();
        WaypointData.Type[] types = WaypointData.Type.values();
        if (typeId >= types.length) {
            throw new IllegalArgumentException("Invalid waypoint-data type: " + typeId);
        }
        return switch (types[typeId]) {
            case UPDATES -> WaypointData.updates(DimensionWaypointsListCodec.decode(buf, context));
            case DIMENSION -> WaypointData.dimension(DimensionWaypointCodec.decode(buf, context));
            case WAYPOINT_LIST -> {
                DimensionWaypointData dimension = DimensionWaypointCodec.decode(buf, context);
                if (dimension.waypointLists().size() != 1) {
                    throw new IllegalArgumentException("Waypoint-list data must contain exactly one list");
                }
                yield WaypointData.waypointList(dimension.dimensionName(), dimension.waypointLists().get(0));
            }
            case WORLD -> WaypointData.world(DimensionWaypointsListCodec.decode(buf, context));
            case UPLOAD -> decodeUpload(buf, context);
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
                requestId,
                statuses[statusId],
                DimensionWaypointsListCodec.decode(buf, context)
        );
    }
}
