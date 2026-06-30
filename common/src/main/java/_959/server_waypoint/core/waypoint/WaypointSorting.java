package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.ColorUtils;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class WaypointSorting {
    public enum SortMode {
        DEFAULT,
        NAME,
        DISTANCE,
        COLOR
    }

    private WaypointSorting() {
    }

    public static Comparator<SimpleWaypoint> byName() {
        return Comparator.comparing(SimpleWaypoint::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SimpleWaypoint::name);
    }

    public static Comparator<SimpleWaypoint> byDistanceFrom(@Nullable WaypointPos origin) {
        return Comparator.comparingLong((SimpleWaypoint waypoint) -> distanceSquared(waypoint, origin))
                .thenComparing(byName());
    }

    public static Comparator<SimpleWaypoint> byColor() {
        return Comparator.comparingLong((SimpleWaypoint waypoint) -> ColorUtils.oklchColorSortKey(waypoint.rgb()))
                .thenComparing(byName());
    }

    public static void sort(List<SimpleWaypoint> waypoints, SortMode sortMode, @Nullable WaypointPos origin) {
        SortMode resolvedSortMode = sortMode == null ? SortMode.DEFAULT : sortMode;
        switch (resolvedSortMode) {
            case DEFAULT -> {
            }
            case NAME -> waypoints.sort(byName());
            case DISTANCE -> waypoints.sort(byDistanceFrom(origin));
            case COLOR -> sortByColor(waypoints);
        }
    }

    public static void sortByColor(List<SimpleWaypoint> waypoints) {
        ColorUtils.sortWaypointColors(
                waypoints,
                SimpleWaypoint::rgb,
                byName()
        );
    }

    public static @Nullable Comparator<SimpleWaypoint> comparator(SortMode sortMode, @Nullable WaypointPos origin) {
        SortMode resolvedSortMode = sortMode == null ? SortMode.DEFAULT : sortMode;
        return switch (resolvedSortMode) {
            case DEFAULT -> null;
            case NAME -> byName();
            case DISTANCE -> byDistanceFrom(origin);
            case COLOR -> byColor();
        };
    }

    public static long distanceSquared(SimpleWaypoint waypoint, @Nullable WaypointPos origin) {
        if (origin == null) {
            return Long.MAX_VALUE;
        }
        long dx = waypoint.x() - origin.x();
        long dy = waypoint.y() - origin.y();
        long dz = waypoint.z() - origin.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
