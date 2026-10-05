package _959.server_waypoint.handoff;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.CompletableFuture;
import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Offline destination permission lookup, with optional LuckPerms support. */
final class PaperOfflineTeleportPermission {
    private PaperOfflineTeleportPermission() { }

    static CompletionStage<TeleportPermissionCheck> check(JavaPlugin plugin, UUID playerId) {
        CompletableFuture<TeleportPermissionCheck> result = new CompletableFuture<>();
        if (!plugin.isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Destination stopped"));
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                if (!plugin.isEnabled()) { result.completeExceptionally(new IllegalStateException("Destination stopped")); return; }
                boolean operator = Bukkit.getOperators().stream().anyMatch(player -> player.getUniqueId().equals(playerId));
                if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                    LuckPermsLookup.check(playerId, operator).whenComplete((allowed, failure) -> {
                        if (failure != null) result.completeExceptionally(failure);
                        else result.complete(allowed);
                    });
                } else {
                    // Bukkit has no general offline permission API. Only its native operator fallback is available.
                    result.complete(new TeleportPermissionCheck(operator, operator));
                }
            } catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    static final class LuckPermsLookup {
        static CompletionStage<TeleportPermissionCheck> check(UUID playerId, boolean operator) {
            return check(LuckPermsProvider.get(), playerId, operator);
        }

        static CompletionStage<TeleportPermissionCheck> check(net.luckperms.api.LuckPerms api, UUID playerId, boolean operator) {
            return api.getUserManager().loadUser(playerId).thenApply(user -> {
                var options = api.getContextManager().getStaticQueryOptions();
                var permissions = user.getCachedData().getPermissionData(options);
                var teleport = permissions.checkPermission("server_waypoint.command.tp");
                var remoteTeleport = permissions.checkPermission("server_waypoint.command.remote.tp");
                return new TeleportPermissionCheck(
                        teleport == net.luckperms.api.util.Tristate.UNDEFINED ? operator : teleport.asBoolean(),
                        remoteTeleport == net.luckperms.api.util.Tristate.UNDEFINED ? operator : remoteTeleport.asBoolean());
            });
        }
    }
}
