package _959.server_waypoint.navigation;

import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Persistent navigation identity. Waypoint display properties are deliberately
 * resolved from the current server data instead of being stored as a snapshot.
 */
public record StoredNavigationSession(
        WaypointCollectionKey collectionKey,
        String dimensionName,
        String listName,
        String waypointName,
        Set<NavigationMethod> enabledMethods,
        TextDisplayTransformation textDisplayTransformation,
        float worldMarkerSize,
        float worldMarkerDistance
) {
    public StoredNavigationSession {
        Objects.requireNonNull(collectionKey, "collectionKey");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(waypointName, "waypointName");
        enabledMethods = NavigationMethod.immutableSet(
                Objects.requireNonNull(enabledMethods, "enabledMethods")
        );
        Objects.requireNonNull(textDisplayTransformation, "textDisplayTransformation");
        NavigationSession.validateWorldMarkerSize(worldMarkerSize);
        NavigationSession.validateWorldMarkerDistance(worldMarkerDistance);
    }

    public Optional<NavigationTarget> resolve(
            UUID playerUuid,
            Function<WaypointCollectionKey, WaypointFilesManagerCore> collectionResolver
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(collectionResolver, "collectionResolver");
        if (!this.collectionKey.accessibleBy(playerUuid)) {
            return Optional.empty();
        }
        WaypointFilesManagerCore waypointFiles = collectionResolver.apply(this.collectionKey);
        if (waypointFiles == null) {
            return Optional.empty();
        }
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
                this.collectionKey,
                fileManager.getDimensionName(),
                waypointList,
                waypoint
        ));
    }
}
