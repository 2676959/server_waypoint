package _959.server_waypoint.common.client;

import _959.server_waypoint.core.collection.WaypointScope;

public enum ClientWaypointSource {
    SHARED, PRIVATE, LOCAL;

    public static ClientWaypointSource fromScope(WaypointScope scope) {
        return scope == WaypointScope.SHARED ? SHARED : PRIVATE;
    }

    public WaypointScope serverScope() {
        if (this == LOCAL) throw new IllegalStateException("Local has no server scope");
        return this == SHARED ? WaypointScope.SHARED : WaypointScope.PRIVATE;
    }
}
