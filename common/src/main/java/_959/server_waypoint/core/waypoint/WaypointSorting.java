package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.util.ColorUtils;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.BlockPosConverter.netherToOverWorld;
import static _959.server_waypoint.util.BlockPosConverter.overWorldToNether;
import static _959.server_waypoint.util.VanillaDimensionNames.MINECRAFT_OVERWORLD;
import static _959.server_waypoint.util.VanillaDimensionNames.MINECRAFT_THE_NETHER;

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

    public static Comparator<SimpleWaypoint> byDistanceFrom(
            @Nullable WaypointPos origin,
            @Nullable String originDimension,
            @Nullable String waypointDimension
    ) {
        return Comparator.comparingDouble((SimpleWaypoint waypoint) -> distanceSquared(
                        waypoint,
                        origin,
                        originDimension,
                        waypointDimension
                ))
                .thenComparing(byName());
    }

    public static Comparator<SimpleWaypoint> byColor() {
        return Comparator.comparingLong((SimpleWaypoint waypoint) -> ColorUtils.oklchColorSortKey(waypoint.rgb()))
                .thenComparing(byName());
    }

    public static void sort(List<SimpleWaypoint> waypoints, SortMode sortMode, @Nullable WaypointPos origin) {
        sort(waypoints, sortMode, origin, false);
    }

    public static void sort(List<SimpleWaypoint> waypoints, SortMode sortMode, @Nullable WaypointPos origin, boolean reversed) {
        sort(waypoints, sortMode, origin, null, null, reversed);
    }

    public static void sort(
            List<SimpleWaypoint> waypoints,
            SortMode sortMode,
            @Nullable WaypointPos origin,
            @Nullable String originDimension,
            @Nullable String waypointDimension,
            boolean reversed
    ) {
        SortMode resolvedSortMode = sortMode == null ? SortMode.DEFAULT : sortMode;
        switch (resolvedSortMode) {
            case DEFAULT -> {
            }
            case NAME -> waypoints.sort(byName());
            case DISTANCE -> waypoints.sort(byDistanceFrom(origin, originDimension, waypointDimension));
            case COLOR -> sortByColor(waypoints);
        }
        if (resolvedSortMode != SortMode.DEFAULT && reversed) {
            Collections.reverse(waypoints);
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

    public static double distanceSquared(
            SimpleWaypoint waypoint,
            @Nullable WaypointPos origin,
            @Nullable String originDimension,
            @Nullable String waypointDimension
    ) {
        if (origin == null) {
            return Double.MAX_VALUE;
        }
        WaypointPos convertedPosition = convertPosition(waypoint.pos(), waypointDimension, originDimension);
        double dx = convertedPosition.x() - origin.x();
        double dy = convertedPosition.y() - origin.y();
        double dz = convertedPosition.z() - origin.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static WaypointPos convertPosition(
            WaypointPos position,
            @Nullable String fromDimension,
            @Nullable String toDimension
    ) {
        if (MINECRAFT_THE_NETHER.equals(fromDimension) && MINECRAFT_OVERWORLD.equals(toDimension)) {
            return netherToOverWorld(position);
        }
        if (MINECRAFT_OVERWORLD.equals(fromDimension) && MINECRAFT_THE_NETHER.equals(toDimension)) {
            return overWorldToNether(position);
        }
        return position;
    }
}
