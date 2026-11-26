package _959.server_waypoint.navigation;

import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * An immutable copy of the waypoint properties needed by navigation.
 */
public record NavigationTarget(
        WaypointCollectionKey collectionKey,
        String dimensionName,
        String listName,
        String listDisplayName,
        String waypointName,
        String waypointDisplayName,
        String waypointDescription,
        List<String> waypointKeywords,
        WaypointPos position,
        int rgb,
        String waypointInitials,
        @Nullable NamespacedId waypointIcon
) {
    public NavigationTarget {
        Objects.requireNonNull(collectionKey, "collectionKey");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(listDisplayName, "listDisplayName");
        Objects.requireNonNull(waypointName, "waypointName");
        Objects.requireNonNull(waypointDisplayName, "waypointDisplayName");
        Objects.requireNonNull(waypointDescription, "waypointDescription");
        waypointKeywords = List.copyOf(waypointKeywords);
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(waypointInitials, "waypointInitials");
    }

    public NavigationTarget(
            WaypointCollectionKey collectionKey,
            String dimensionName,
            WaypointList waypointList,
            SimpleWaypoint waypoint
    ) {
        this(collectionKey, dimensionName, waypointList.name(), waypointList.displayName(), snapshot(waypoint));
    }

    public NavigationTarget withListIdentity(String newListName, String newListDisplayName) {
        return new NavigationTarget(
                this.collectionKey,
                this.dimensionName,
                newListName,
                newListDisplayName,
                this.waypointName,
                this.waypointDisplayName,
                this.waypointDescription,
                this.waypointKeywords,
                this.position,
                this.rgb,
                this.waypointInitials,
                this.waypointIcon
        );
    }

    private NavigationTarget(
            WaypointCollectionKey collectionKey,
            String dimensionName,
            String listName,
            String listDisplayName,
            WaypointSnapshot waypointSnapshot
    ) {
        this(
                collectionKey,
                dimensionName,
                listName,
                listDisplayName,
                waypointSnapshot.name(),
                waypointSnapshot.displayName(),
                waypointSnapshot.description(),
                waypointSnapshot.keywords(),
                waypointSnapshot.position(),
                waypointSnapshot.rgb(),
                waypointSnapshot.initials(),
                waypointSnapshot.icon()
        );
    }

    private static WaypointSnapshot snapshot(SimpleWaypoint waypoint) {
        SimpleWaypoint snapshot = new SimpleWaypoint(Objects.requireNonNull(waypoint, "waypoint"));
        return new WaypointSnapshot(
                snapshot.name(),
                snapshot.displayName(),
                snapshot.description(),
                snapshot.keywords(),
                snapshot.pos(),
                snapshot.rgb(),
                snapshot.initials(),
                snapshot.icon()
        );
    }

    private record WaypointSnapshot(
            String name,
            String displayName,
            String description,
            List<String> keywords,
            WaypointPos position,
            int rgb,
            String initials,
            @Nullable NamespacedId icon
    ) {
    }
}
