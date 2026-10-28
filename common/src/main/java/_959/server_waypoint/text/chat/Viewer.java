package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.util.StringCommandBuilder;
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
        float yaw,
        ChatIcons icons,
        WaypointScope scope
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
        REMOTE_TP,
        PRIVATE,
        LIST
    }

    public Viewer {
        icons = plainText ? ChatIcons.NONE : java.util.Objects.requireNonNull(icons, "icons");
        permissions = permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public Viewer(Set<Permission> permissions, boolean hasMod, boolean plainText,
                  @Nullable String dimension, @Nullable WaypointPos position, float yaw, ChatIcons icons) {
        this(permissions, hasMod, plainText, dimension, position, yaw, icons, WaypointScope.SHARED);
    }

    public Viewer withScope(WaypointScope scope) {
        return new Viewer(permissions, hasMod, plainText, dimension, position, yaw, icons, scope);
    }

    public String command(String command) {
        return StringCommandBuilder.inScope(scope, command);
    }

    /** A text-only viewer, including builds without native chat objects. */
    public Viewer(Set<Permission> permissions, boolean hasMod, boolean plainText,
                  @Nullable String dimension, @Nullable WaypointPos position, float yaw) {
        this(permissions, hasMod, plainText, dimension, position, yaw, ChatIcons.NONE);
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
