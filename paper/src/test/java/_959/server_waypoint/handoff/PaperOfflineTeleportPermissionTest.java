package _959.server_waypoint.handoff;

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
    private Object options;

    @Test void waitsForDestinationUserAndExplicitDenialOverridesOperator() {
        var result = PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, true).toCompletableFuture();
        assertFalse(result.isDone());
        permission = Tristate.FALSE;
        loaded.complete(api(User.class));
        assertFalse(result.join());
    }

    @Test void grantsNonOperatorAndUsesOperatorFallbackOnlyForUndefined() {
        permission = Tristate.TRUE;
        loaded.complete(api(User.class));
        assertTrue(check(false));
        permission = Tristate.UNDEFINED;
        assertFalse(check(false));
        assertTrue(check(true));
    }

    @Test void providerFailureCannotGrantAnOperator() {
        var result = PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, true).toCompletableFuture();
        loaded.completeExceptionally(new IllegalStateException("unavailable"));
        assertThrows(java.util.concurrent.CompletionException.class, result::join);
    }

    private boolean check(boolean operator) {
        return PaperOfflineTeleportPermission.LuckPermsLookup.check(api(LuckPerms.class), player, operator).toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    private <T> T api(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "loadUser": assertEquals(player, args[0]); return loaded;
                case "getStaticQueryOptions": options = api(method.getReturnType()); return options;
                case "getPermissionData": assertSame(options, args[0]); return api(method.getReturnType());
                case "checkPermission": assertEquals("server_waypoint.command.tp", args[0]); return permission;
                default: return api(method.getReturnType());
            }
        });
    }
}
