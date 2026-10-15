package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The selectable image suffixes packaged by the supported VoxelMap dependency. */
public final class VoxelMapIconIds {
    private static final NamespacedId DEFAULT = new NamespacedId("voxelmap", "waypoint");
    private static final List<NamespacedId> IDS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(DEFAULT),
            WaypointIconPolicy.voxelMapSuffixes().stream().sorted().map(suffix -> new NamespacedId("voxelmap", suffix))
    ).toList();

    private VoxelMapIconIds() {
    }

    public static @Nullable NamespacedId fromSuffix(String suffix) {
        if (suffix == null) return null;
        if (suffix.isEmpty()) return DEFAULT;
        return WaypointIconPolicy.voxelMapSuffixes().contains(suffix)
                ? new NamespacedId("voxelmap", suffix) : null;
    }

    public static String toSuffix(@Nullable NamespacedId icon) {
        return icon != null && icon.namespace().equals("voxelmap")
                && WaypointIconPolicy.voxelMapSuffixes().contains(icon.path())
                ? icon.path() : "";
    }

    public static List<NamespacedId> ids() {
        return IDS;
    }
}
