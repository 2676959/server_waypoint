package _959.server_waypoint.navigation;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent navigation identity. Waypoint display properties are deliberately
 * resolved from the current server data instead of being stored as a snapshot.
 */
public record StoredNavigationSession(
        String dimensionName,
        String listName,
        String waypointName,
        Set<NavigationMethod> enabledMethods,
        TextDisplayTransformation textDisplayTransformation
) {
    public StoredNavigationSession {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(waypointName, "waypointName");
        enabledMethods = NavigationMethod.immutableSet(
                Objects.requireNonNull(enabledMethods, "enabledMethods")
        );
        Objects.requireNonNull(textDisplayTransformation, "textDisplayTransformation");
    }

    public Optional<NavigationTarget> resolve(WaypointFilesManagerCore waypointFiles) {
        Objects.requireNonNull(waypointFiles, "waypointFiles");
        WaypointFileManager fileManager = waypointFiles.getWaypointFileManager(this.dimensionName);
        if (fileManager == null) {
            return Optional.empty();
        }
        WaypointList waypointList = fileManager.getWaypointListByName(this.listName);
        if (waypointList == null) {
            return Optional.empty();
        }
        SimpleWaypoint waypoint = waypointList.getWaypointByName(this.waypointName);
        if (waypoint == null) {
            return Optional.empty();
        }
        return Optional.of(new NavigationTarget(
                fileManager.getDimensionName(),
                waypointList,
                waypoint
        ));
    }
}
