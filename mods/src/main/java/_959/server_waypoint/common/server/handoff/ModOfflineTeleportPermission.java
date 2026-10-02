package _959.server_waypoint.common.server.handoff;

import _959.server_waypoint.command.permission.PermissionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Reads the destination operator list on its server thread before querying an offline provider. */
final class ModOfflineTeleportPermission {
    private final MinecraftServer server;
    private final PermissionManager<?, String, ServerPlayer> permissions;

    ModOfflineTeleportPermission(MinecraftServer server, PermissionManager<?, String, ServerPlayer> permissions) {
        this.server = server;
        this.permissions = permissions;
    }
    public CompletionStage<Boolean> check(UUID playerId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (server.isStopped()) return CompletableFuture.completedFuture(false);
        server.execute(() -> {
            try {
                if (server.isStopped()) { result.complete(false); return; }
                int required = _959.server_waypoint.core.WaypointServerCore.CONFIG.CommandPermission().tp();
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
                permissions.checkOfflinePermission(playerId, permissions.keys.tp(), level >= required)
                        .whenComplete((allowed, failure) -> {
                            if (failure != null) result.completeExceptionally(failure);
                            else result.complete(Boolean.TRUE.equals(allowed));
                        });
            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
}
