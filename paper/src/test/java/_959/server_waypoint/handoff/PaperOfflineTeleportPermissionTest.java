package _959.server_waypoint.handoff;

import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class PaperOfflineTeleportPermissionTest {
    private final UUID player = new UUID(17, 31);
    private final CompletableFuture<User> loaded = new CompletableFuture<>();
    private Tristate permission = Tristate.UNDEFINED;
    private Tristate remotePermission = Tristate.UNDEFINED;
    private Object options;

    @Test void waitsForDestinationUserAndExplicitDenialOverridesOperator() {
        var result = PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, fallback(true)).toCompletableFuture();
        assertFalse(result.isDone());
        permission = Tristate.FALSE;
        loaded.complete(api(User.class));
        assertFalse(result.join().allowed());
    }

    @Test void grantsNonOperatorAndUsesOperatorFallbackOnlyForUndefined() {
        permission = Tristate.TRUE;
        remotePermission = Tristate.TRUE;
        loaded.complete(api(User.class));
        assertTrue(check(false));
        permission = Tristate.UNDEFINED;
        remotePermission = Tristate.UNDEFINED;
        assertFalse(check(false));
        assertTrue(check(true));
    }

    @Test void teleportDenialStillReportsRemoteGrant() {
        permission = Tristate.FALSE;
        remotePermission = Tristate.TRUE;
        loaded.complete(api(User.class));
        assertEquals(new _959.server_waypoint.crossserver.TeleportPermissionCheck(false, true),
                PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, fallback(true))
                        .toCompletableFuture().join());
    }

    @Test void localTeleportGrantCannotOverrideRemoteTeleportDenial() {
        permission = Tristate.TRUE;
        remotePermission = Tristate.FALSE;
        loaded.complete(api(User.class));
        assertFalse(check(false));
        assertFalse(check(true));
    }

    @Test void nonOperatorNeedsBothDestinationGrants() {
        permission = Tristate.TRUE;
        loaded.complete(api(User.class));
        assertFalse(check(false));
        remotePermission = Tristate.TRUE;
        assertTrue(check(false));
        permission = Tristate.FALSE;
        assertFalse(check(false));
        assertFalse(check(true));
    }

    @Test void providerFailureCannotGrantAnOperator() {
        var result = PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, fallback(true)).toCompletableFuture();
        loaded.completeExceptionally(new IllegalStateException("unavailable"));
        assertThrows(java.util.concurrent.CompletionException.class, result::join);
    }

    private boolean check(boolean operator) {
        return PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, fallback(operator)).toCompletableFuture().join().allowed();
    }

    private static TeleportPermissionCheck fallback(boolean allowed) {
        return new TeleportPermissionCheck(allowed, allowed);
    }

    @SuppressWarnings("unchecked")
    private <T> T api(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "loadUser": assertEquals(player, args[0]); return loaded;
                case "getStaticQueryOptions": options = api(method.getReturnType()); return options;
                case "getPermissionData": assertSame(options, args[0]); return api(method.getReturnType());
                case "checkPermission":
                    return switch ((String) args[0]) {
                        case "server_waypoint.command.tp" -> permission;
                        case "server_waypoint.command.remote.tp" -> remotePermission;
                        default -> throw new AssertionError("Unexpected permission node: " + args[0]);
                    };
                default: return api(method.getReturnType());
            }
        });
    }
}
