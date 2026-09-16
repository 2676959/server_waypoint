package _959.server_waypoint.core.network.data;

import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Logical waypoint data carried by the unified, chunked waypoint-data channel.
 */
public record WaypointData(
        Type type,
        List<DimensionWaypointData> dimensions,
        Upload upload
) implements ChunkedMessage {
    public WaypointData {
        Objects.requireNonNull(type, "type");
        dimensions = List.copyOf(dimensions);
        if (type == Type.UPLOAD) {
            Objects.requireNonNull(upload, "upload");
            if (upload.status() != UploadStatus.SUCCESS && !dimensions.isEmpty()) {
                throw new IllegalArgumentException("Failed upload data cannot contain dimensions");
            }
        } else {
            if (upload != null) {
                throw new IllegalArgumentException("Only upload data can contain upload metadata");
            }
            if ((type == Type.DIMENSION || type == Type.WAYPOINT_LIST) && dimensions.size() != 1) {
                throw new IllegalArgumentException(type + " data must contain exactly one dimension");
            }
            if (type == Type.WAYPOINT_LIST && dimensions.get(0).waypointLists().size() != 1) {
                throw new IllegalArgumentException("Waypoint-list data must contain exactly one list");
            }
        }
    }

    public static WaypointData updates(Collection<DimensionWaypointData> dimensions) {
        return new WaypointData(Type.UPDATES, List.copyOf(dimensions), null);
    }

    public static WaypointData dimension(DimensionWaypointData dimension) {
        return new WaypointData(Type.DIMENSION, List.of(dimension), null);
    }

    public static WaypointData waypointList(String dimensionName, WaypointList waypointList) {
        return dimension(Type.WAYPOINT_LIST, new DimensionWaypointData(dimensionName, List.of(waypointList)));
    }

    public static WaypointData world(Collection<DimensionWaypointData> dimensions) {
        return new WaypointData(Type.WORLD, List.copyOf(dimensions), null);
    }

    public static WaypointData upload(
            UUID requestId,
            UploadStatus status,
            Collection<DimensionWaypointData> dimensions
    ) {
        return new WaypointData(
                Type.UPLOAD,
                List.copyOf(dimensions),
                new Upload(requestId, status)
        );
    }

    private static WaypointData dimension(Type type, DimensionWaypointData dimension) {
        return new WaypointData(type, List.of(dimension), null);
    }

    public DimensionWaypointData singleDimension() {
        if (this.type != Type.DIMENSION && this.type != Type.WAYPOINT_LIST) {
            throw new IllegalStateException(this.type + " data does not have one dimension");
        }
        return this.dimensions.get(0);
    }

    public Upload uploadData() {
        if (this.type != Type.UPLOAD) {
            throw new IllegalStateException(this.type + " data is not an upload");
        }
        return this.upload;
    }

    @Override
    public ChunkedMessageType<WaypointData> getType() {
        return ChunkedMessageRegistry.WAYPOINT_DATA;
    }

    public enum Type {
        UPDATES,
        DIMENSION,
        WAYPOINT_LIST,
        WORLD,
        UPLOAD
    }

    public record Upload(
            UUID requestId,
            UploadStatus status
    ) {
        public Upload {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(status, "status");
        }
    }
}
