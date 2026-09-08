package _959.server_waypoint.crossserver;

import _959.server_waypoint.command.permission.*;
import _959.server_waypoint.config.CommandPermission;
import _959.server_waypoint.crossserver.authorization.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static _959.server_waypoint.crossserver.authorization.DestinationAuthorization.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteAuthorizationTest {
    private final RemoteServerId destination = new RemoteServerId("destination");
    private final RemoteWaypointKey key = new RemoteWaypointKey(destination, "world", "Public", "Base");

    @Test void exactDestinationAndIdentityAreCheckedAgainAfterPreparation() {
        AtomicReference<RemoteWaypointKey> exported = new AtomicReference<>(key);
        AtomicBoolean permission = new AtomicBoolean(true);
        var authorization = new DestinationAuthorization<Object>(destination,
                target -> target.equals(exported.get()), player -> permission.get());
        assertEquals(WRONG_DESTINATION, authorization.prepare(new RemoteWaypointKey(
                new RemoteServerId("source"), "world", "Public", "Base")));
        assertEquals(NOT_EXPORTED, authorization.prepare(new RemoteWaypointKey(destination, "world", "public", "Base")));
        assertEquals(ALLOWED, authorization.prepare(key));
        permission.set(false);
        assertEquals(PERMISSION_DENIED, authorization.arrive(key, new Object()));
        permission.set(true);
        assertEquals(ALLOWED, authorization.arrive(key, new Object()));
        exported.set(null);
        assertEquals(NOT_EXPORTED, authorization.arrive(key, new Object()));
        assertEquals(PLAYER_REQUIRED, authorization.arrive(key, null));
    }

    @Test void unavailableCallbacksFailClosedWithoutConsultingOfflinePermissions() {
        AtomicInteger checks = new AtomicInteger();
        var authorization = new DestinationAuthorization<Object>(destination,
                target -> { throw new IllegalStateException("unavailable"); }, player -> { checks.incrementAndGet(); return true; });
        assertEquals(UNAVAILABLE, authorization.prepare(key));
        assertEquals(UNAVAILABLE, authorization.arrive(key, new Object()));
        assertEquals(0, checks.get());
        var failedPermission = new DestinationAuthorization<Object>(destination, target -> true,
                player -> { throw new IllegalStateException("permission provider unavailable"); });
        assertEquals(ALLOWED, failedPermission.prepare(key));
        assertEquals(UNAVAILABLE, failedPermission.arrive(key, new Object()));
    }

    @Test void sourceUsesBothPlayerNodesAndCurrentConfiguredLevelsWhileArrivalUsesOnlyLocalTp() {
        Map<String, Boolean> allowed = new HashMap<>();
        List<String> checks = new ArrayList<>();
        Object player = new Object();
        PermissionManager<String, String, Object> manager = new PermissionManager<>(new PermissionStringKeys()) {
            @Override public boolean hasPermission(String source, PermissionKeys<String>.PermissionKey key, int level) {
                checks.add("source:" + key.getKey() + ":" + level);
                return allowed.getOrDefault(key.getKey(), level == 0);
            }
            @Override public boolean checkPlayerPermission(Object actual, PermissionKeys<String>.PermissionKey key, int level) {
                assertSame(player, actual);
                checks.add("player:" + key.getKey() + ":" + level);
                return allowed.getOrDefault(key.getKey(), level == 0);
            }
        };
        AtomicReference<CommandPermission> config = new AtomicReference<>(new CommandPermission());
        var permissions = new RemotePermissions<>(manager, config::get, source -> source.equals("console") ? null : player);
        assertTrue(permissions.canList("console"));
        assertFalse(permissions.canRequestTeleport("console"));
        allowed.put("server_waypoint.command.remote.tp", true);
        assertFalse(permissions.canRequestTeleport("player"));
        allowed.put("server_waypoint.command.tp", true);
        assertTrue(permissions.canRequestTeleport("player"));
        allowed.put("server_waypoint.command.remote.tp", false);
        assertFalse(permissions.canRequestTeleport("player"));
        assertTrue(permissions.canTeleportOnArrival(player));
        allowed.put("server_waypoint.command.tp", false);
        assertFalse(permissions.canTeleportOnArrival(player));
        assertFalse(permissions.canTeleportOnArrival(null));
        config.set(new Gson().fromJson("{\"remoteList\":3,\"remoteTp\":4,\"tp\":1}", CommandPermission.class));
        permissions.canList("console");
        allowed.put("server_waypoint.command.tp", true);
        permissions.canRequestTeleport("player");
        assertTrue(checks.contains("source:server_waypoint.command.remote.list:3"));
        assertTrue(checks.contains("player:server_waypoint.command.tp:1"));
        assertTrue(checks.contains("player:server_waypoint.command.remote.tp:4"));
    }
}
