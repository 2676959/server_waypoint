package _959.server_waypoint.core.network.data;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageType;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Logical waypoint data carried by the unified, chunked waypoint-data channel.
 */
public record WaypointData(
        WaypointScope scope,
        Type type,
        List<DimensionWaypointData> dimensions,
        Upload upload
) implements WaypointScopedMessage {
    public WaypointData {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(type, "type");
        dimensions = List.copyOf(dimensions);
        if (type == Type.UPLOAD) {
            if (scope != WaypointScope.SHARED) throw new IllegalArgumentException("Private uploads are forbidden");
            Objects.requireNonNull(upload, "upload");
            if (upload.status() != UploadStatus.SUCCESS && !dimensions.isEmpty()) {
                throw new IllegalArgumentException("Failed upload data cannot contain dimensions");
            }
        } else {
            if (upload != null) {
                throw new IllegalArgumentException("Only upload data can contain upload metadata");
            }
            if ((type == Type.DIMENSION || type == Type.WAYPOINT_LIST || type == Type.WAYPOINT) && dimensions.size() != 1) {
                throw new IllegalArgumentException(type + " data must contain exactly one dimension");
            }
            if ((type == Type.WAYPOINT_LIST || type == Type.WAYPOINT) && dimensions.get(0).waypointLists().size() != 1) {
                throw new IllegalArgumentException("Waypoint-list data must contain exactly one list");
            }
            if (type == Type.WAYPOINT && dimensions.get(0).waypointLists().get(0).size() != 1) {
                throw new IllegalArgumentException("Waypoint data must contain exactly one waypoint");
            }
        }
    }

    public static WaypointData updates(WaypointScope scope, Collection<DimensionWaypointData> dimensions) {
        return new WaypointData(scope, Type.UPDATES, List.copyOf(dimensions), null);
    }

    public static WaypointData dimension(WaypointScope scope, DimensionWaypointData dimension) {
        return new WaypointData(scope, Type.DIMENSION, List.of(dimension), null);
    }

    public static WaypointData waypointList(WaypointScope scope, String dimensionName, WaypointList waypointList) {
        return dimension(scope, Type.WAYPOINT_LIST, new DimensionWaypointData(dimensionName, List.of(waypointList)));
    }

    public static WaypointData world(WaypointScope scope, Collection<DimensionWaypointData> dimensions) {
        return new WaypointData(scope, Type.WORLD, List.copyOf(dimensions), null);
    }

    /** A manual single-waypoint snapshot, independent of incremental revision checks. */
    public static WaypointData waypoint(WaypointScope scope, String dimensionName, WaypointList list, SimpleWaypoint waypoint) {
        return dimension(scope, Type.WAYPOINT, new DimensionWaypointData(dimensionName, List.of(
                new WaypointList(list.name(), list.displayName(), list.getSyncNum(), List.of(waypoint))
        )));
    }

    public static WaypointData upload(
            WaypointScope scope,
            UUID requestId,
            UploadStatus status,
            Collection<DimensionWaypointData> dimensions
    ) {
        return new WaypointData(
                scope, Type.UPLOAD,
                List.copyOf(dimensions),
                new Upload(requestId, status)
        );
    }

    private static WaypointData dimension(WaypointScope scope, Type type, DimensionWaypointData dimension) {
        return new WaypointData(scope, type, List.of(dimension), null);
    }

    public DimensionWaypointData singleDimension() {
        if (this.type != Type.DIMENSION && this.type != Type.WAYPOINT_LIST && this.type != Type.WAYPOINT) {
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
        UPLOAD,
        WAYPOINT
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
