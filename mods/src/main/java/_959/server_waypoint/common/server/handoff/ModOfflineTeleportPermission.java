package _959.server_waypoint.common.server.handoff;

import _959.server_waypoint.command.permission.PermissionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import java.util.concurrent.CompletionStage;

/** Reads the destination operator list on its server thread before querying an offline provider. */
final class ModOfflineTeleportPermission {
    private final MinecraftServer server;
    private final PermissionManager<?, String, ServerPlayer> permissions;

    ModOfflineTeleportPermission(MinecraftServer server, PermissionManager<?, String, ServerPlayer> permissions) {
        this.server = server;
        this.permissions = permissions;
    }
    public CompletionStage<TeleportPermissionCheck> check(UUID playerId) {
        CompletableFuture<TeleportPermissionCheck> result = new CompletableFuture<>();
        if (server.isStopped()) return CompletableFuture.failedFuture(new IllegalStateException("Destination stopped"));
        server.execute(() -> {
            try {
                if (server.isStopped()) { result.completeExceptionally(new IllegalStateException("Destination stopped")); return; }
                var required = _959.server_waypoint.core.WaypointServerCore.CONFIG.CommandPermission();
                // Operator lists are keyed by UUID; the placeholder name is never used for authorization.
                //? if >=1.21.11 {
                var entry = server.getPlayerList().getOps().get(new net.minecraft.server.players.NameAndId(playerId, ""));
                int level = entry == null ? 0 : entry.permissions().level().id();
                //?} elif >=1.21.9 {
                /*var entry = server.getPlayerList().getOps().get(new net.minecraft.server.players.NameAndId(playerId, ""));
                int level = entry == null ? 0 : entry.getLevel();
                *///?} else {
                /*var entry = server.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(playerId, ""));
                int level = entry == null ? 0 : entry.getLevel();
                *///?}
                permissions.checkOfflinePermission(playerId, permissions.keys.tp(), level >= required.tp())
                        .thenCombine(permissions.checkOfflinePermission(playerId, permissions.keys.remoteTp(), level >= required.remoteTp()),
                                (tp, remoteTp) -> new TeleportPermissionCheck(
                                        Boolean.TRUE.equals(tp), Boolean.TRUE.equals(remoteTp)))
                        .whenComplete((allowed, failure) -> {
                            if (failure != null) result.completeExceptionally(failure);
                            else result.complete(allowed);
                        });
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
}
