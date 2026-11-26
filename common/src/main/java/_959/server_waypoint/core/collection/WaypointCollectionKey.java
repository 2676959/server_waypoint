package _959.server_waypoint.core.collection;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Server-side collection identity; private owners always come from authenticated players. */
public record WaypointCollectionKey(WaypointScope scope, @Nullable UUID ownerUuid) {
    public WaypointCollectionKey {
        Objects.requireNonNull(scope, "scope");
        if ((scope == WaypointScope.PRIVATE) != (ownerUuid != null)) {
            throw new IllegalArgumentException("Only private collections require an owner");
        }
    }

    public static WaypointCollectionKey shared() {
        return new WaypointCollectionKey(WaypointScope.SHARED, null);
    }

    public static WaypointCollectionKey privateFor(UUID ownerUuid) {
        return new WaypointCollectionKey(WaypointScope.PRIVATE, Objects.requireNonNull(ownerUuid, "ownerUuid"));
    }

    public boolean accessibleBy(UUID playerUuid) {
        return this.scope == WaypointScope.SHARED || this.ownerUuid.equals(playerUuid);
    }
}
