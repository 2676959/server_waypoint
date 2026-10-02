package _959.server_waypoint.proxy;

import java.util.UUID;

/** Checks current proxy permission state; an offline/unknown player must be denied. */
public interface ProxyPermissionChecker {
    boolean hasPermission(UUID playerId, String permission);
}
