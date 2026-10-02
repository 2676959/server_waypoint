package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Whoever reads a message, built once per command source: what they may do, whether their client
 * has the mod, whether they read plain text (the console, RCON and command blocks), and where
 * they stand.
 */
public record Viewer(
        Set<Permission> permissions,
        boolean hasMod,
        boolean plainText,
        @Nullable String dimension,
        @Nullable WaypointPos position,
        float yaw
) {
    public enum Permission {
        ADD,
        EDIT,
        REMOVE,
        TP,
        NAVIGATE,
        RELOAD,
        UPLOAD,
        UPLOAD_DELETE,
        REMOTE_LIST,
        REMOTE_TP
    }

    public Viewer {
        permissions = permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public static Set<Permission> everything() {
        return EnumSet.allOf(Permission.class);
    }

    public boolean can(Permission permission) {
        return this.permissions.contains(permission);
    }

    /** Whether the viewer stands in this dimension. */
    public boolean isIn(String dimension) {
        return dimension.equals(this.dimension);
    }
}
