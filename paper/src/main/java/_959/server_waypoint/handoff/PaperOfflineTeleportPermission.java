package _959.server_waypoint.handoff;

import _959.server_waypoint.core.WaypointServerCore;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
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
                var server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
                // The destination owns the fallback level; the placeholder name is not used for authorization.
                //? if >=1.21.11 {
                int level = server.getProfilePermissions(new net.minecraft.server.players.NameAndId(playerId, "")).level().id();
                //?} elif >=1.21.9 {
                /*int level = server.getProfilePermissions(new net.minecraft.server.players.NameAndId(playerId, ""));
                *///?} else {
                /*int level = server.getProfilePermissions(new com.mojang.authlib.GameProfile(playerId, ""));
                *///?}
                var required = WaypointServerCore.CONFIG.CommandPermission();
                var fallback = new TeleportPermissionCheck(level >= required.tp(), level >= required.remoteTp());
                if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                    LuckPermsLookup.check(playerId, fallback).whenComplete((allowed, failure) -> {
                        if (failure != null) result.completeExceptionally(failure);
                        else result.complete(allowed);
                    });
                } else {
                    result.complete(fallback);
                }
            } catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    static final class LuckPermsLookup {
        static CompletionStage<TeleportPermissionCheck> check(UUID playerId, TeleportPermissionCheck fallback) {
            return check(LuckPermsProvider.get(), playerId, fallback);
        }

        static CompletionStage<TeleportPermissionCheck> check(net.luckperms.api.LuckPerms api, UUID playerId,
                TeleportPermissionCheck fallback) {
            return api.getUserManager().loadUser(playerId).thenApply(user -> {
                var options = api.getContextManager().getStaticQueryOptions();
                var permissions = user.getCachedData().getPermissionData(options);
                var teleport = permissions.checkPermission("server_waypoint.command.tp");
                var remoteTeleport = permissions.checkPermission("server_waypoint.command.remote.tp");
                return new TeleportPermissionCheck(
                        teleport == net.luckperms.api.util.Tristate.UNDEFINED ? fallback.tp() : teleport.asBoolean(),
                        remoteTeleport == net.luckperms.api.util.Tristate.UNDEFINED ? fallback.remoteTp() : remoteTeleport.asBoolean());
            });
        }
    }
}
