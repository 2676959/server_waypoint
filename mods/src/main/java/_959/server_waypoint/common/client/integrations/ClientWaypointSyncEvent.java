package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;

import java.util.List;

public record ClientWaypointSyncEvent(
        Type type,
        String dimensionName,
        String listName,
        WaypointList waypointList,
        List<WaypointList> waypointLists,
        SimpleWaypoint waypoint,
        String waypointName,
        WaypointModificationType modificationType
) {
    public ClientWaypointSyncEvent {
        waypointLists = waypointLists == null ? List.of() : List.copyOf(waypointLists);
    }

    public static ClientWaypointSyncEvent allSynced() {
        return new ClientWaypointSyncEvent(Type.ALL_SYNCED, null, null, null, List.of(), null, null, null);
    }

    public static ClientWaypointSyncEvent worldReplaced() {
        return new ClientWaypointSyncEvent(Type.WORLD_REPLACED, null, null, null, List.of(), null, null, null);
    }

    public static ClientWaypointSyncEvent dimensionReplaced(String dimensionName, List<WaypointList> waypointLists) {
        return new ClientWaypointSyncEvent(Type.DIMENSION_REPLACED, dimensionName, null, null, waypointLists, null, null, null);
    }

    public static ClientWaypointSyncEvent listReplaced(String dimensionName, WaypointList waypointList) {
        return new ClientWaypointSyncEvent(Type.LIST_REPLACED, dimensionName, waypointList.name(), waypointList, List.of(), null, null, null);
    }

    public static ClientWaypointSyncEvent waypointModified(
            String dimensionName,
            String listName,
            WaypointModificationType modificationType,
            SimpleWaypoint waypoint,
            String waypointName
    ) {
        return new ClientWaypointSyncEvent(
                typeFromModification(modificationType),
                dimensionName,
                listName,
                null,
                List.of(),
                waypoint,
                waypointName,
                modificationType
        );
    }

    private static Type typeFromModification(WaypointModificationType modificationType) {
        return switch (modificationType) {
            case ADD -> Type.WAYPOINT_ADDED;
            case REMOVE -> Type.WAYPOINT_REMOVED;
            case UPDATE -> Type.WAYPOINT_UPDATED;
            case ADD_LIST -> Type.LIST_ADDED;
            case REMOVE_LIST -> Type.LIST_REMOVED;
        };
    }

    public enum Type {
        ALL_SYNCED,
        WORLD_REPLACED,
        DIMENSION_REPLACED,
        LIST_REPLACED,
        WAYPOINT_ADDED,
        WAYPOINT_REMOVED,
        WAYPOINT_UPDATED,
        LIST_ADDED,
        LIST_REMOVED
    }
}
