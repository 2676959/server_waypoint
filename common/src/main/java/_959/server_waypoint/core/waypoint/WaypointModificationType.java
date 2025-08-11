package _959.server_waypoint.core.waypoint;

public enum WaypointModificationType {
    ADD,
    REMOVE,
    UPDATE;

    public String toVerb() {
        return switch (this) {
            case ADD -> "added";
            case REMOVE -> "removed";
            case UPDATE -> "updated";
        };
    }
}
