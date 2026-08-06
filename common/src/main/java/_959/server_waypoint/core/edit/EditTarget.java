package _959.server_waypoint.core.edit;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record EditTarget(
        Type type,
        String dimensionName,
        String listIdentifier,
        @Nullable String waypointIdentifier
) {
    public EditTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        if (type == Type.WAYPOINT) {
            Objects.requireNonNull(waypointIdentifier, "waypointIdentifier");
        } else if (waypointIdentifier != null) {
            throw new IllegalArgumentException("List targets cannot contain a waypoint identifier");
        }
    }

    public static EditTarget list(String dimensionName, String listIdentifier) {
        return new EditTarget(Type.LIST, dimensionName, listIdentifier, null);
    }

    public static EditTarget waypoint(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier
    ) {
        return new EditTarget(Type.WAYPOINT, dimensionName, listIdentifier, waypointIdentifier);
    }

    public String requiredWaypointIdentifier() {
        if (this.type != Type.WAYPOINT) {
            throw new IllegalStateException("Target is not a waypoint");
        }
        return Objects.requireNonNull(this.waypointIdentifier);
    }

    public enum Type {
        LIST,
        WAYPOINT
    }
}
