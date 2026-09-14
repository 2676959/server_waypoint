package _959.server_waypoint.handoff;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Offline destination permission lookup, with optional LuckPerms support. */
final class PaperOfflineTeleportPermission {
    private PaperOfflineTeleportPermission() { }

    static CompletionStage<Boolean> check(JavaPlugin plugin, UUID playerId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!plugin.isEnabled()) return CompletableFuture.completedFuture(false);
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                if (!plugin.isEnabled()) { result.complete(false); return; }
                boolean operator = Bukkit.getOperators().stream().anyMatch(player -> player.getUniqueId().equals(playerId));
                if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                    LuckPermsLookup.check(playerId, operator).whenComplete((allowed, failure) -> {
                        if (failure != null) result.completeExceptionally(failure);
                        else result.complete(Boolean.TRUE.equals(allowed));
                    });
                } else {
                    // Bukkit has no general offline permission API. Only its native operator fallback is available.
                    result.complete(operator);
                }
            } catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    static final class LuckPermsLookup {
        static CompletionStage<Boolean> check(UUID playerId, boolean operator) {
            return check(LuckPermsProvider.get(), playerId, operator);
        }

        static CompletionStage<Boolean> check(net.luckperms.api.LuckPerms api, UUID playerId, boolean operator) {
            return api.getUserManager().loadUser(playerId).thenApply(user -> {
                var options = api.getContextManager().getStaticQueryOptions();
                var permission = user.getCachedData().getPermissionData(options)
                        .checkPermission("server_waypoint.command.tp");
                return permission == net.luckperms.api.util.Tristate.UNDEFINED ? operator : permission.asBoolean();
            });
        }
    }
}
