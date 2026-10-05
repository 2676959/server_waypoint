package _959.server_waypoint.common.server.handoff;

import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.crossserver.handoff.DestinationPlatform;
import _959.server_waypoint.crossserver.handoff.DestinationResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import java.util.function.Function;

/** Server-bound adapter shared by Fabric, Forge and NeoForge. Lifecycle wiring remains separate. */
public final class ModDestinationPlatform implements DestinationPlatform<ServerPlayer> {
    private final MinecraftServer server;
    private final Function<ServerPlayer, TeleportPermissionCheck> permission;
    private final java.util.function.Function<UUID, CompletionStage<TeleportPermissionCheck>> preflight;

    public ModDestinationPlatform(MinecraftServer server, Function<ServerPlayer, TeleportPermissionCheck> permission,
            java.util.function.Function<UUID, CompletionStage<TeleportPermissionCheck>> preflight) {
        this.server = Objects.requireNonNull(server);
        this.permission = Objects.requireNonNull(permission);
        this.preflight = Objects.requireNonNull(preflight);
    }
    @Override public CompletionStage<TeleportPermissionCheck> canPrepare(UUID playerId) { return preflight.apply(playerId); }
    @Override public boolean execute(ServerPlayer player, Runnable task, Runnable retired) {
        if (server.isStopped()) return false;
        // Join hooks may run before PlayerList installs the player. Always enqueue the owner check.
        Runnable action = () -> {
            if (server.isStopped() || !isCurrentPlayer(player)) retired.run();
            else task.run();
        };
        //? if >=1.21.2 {
        server.schedule(new TickTask(server.getTickCount(), action));
        //?} else {
        /*server.tell(new TickTask(server.getTickCount(), action));
        *///?}
        return true;
    }
    @Override public boolean ownsThread(ServerPlayer player) { return server.isSameThread(); }
    @Override public UUID playerId(ServerPlayer player) { return player.getUUID(); }
    @Override public boolean isCurrentPlayer(ServerPlayer player) {
        return !server.isStopped() && !player.hasDisconnected() && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    @Override public TeleportPermissionCheck checkTeleportPermissions(ServerPlayer player) { return permission.apply(player); }
    @Override public CompletionStage<Boolean> teleport(ServerPlayer player, DestinationResolver.Target target) {
        PlayerActionLog.Actor actor = new PlayerActionLog.Actor(player.getUUID(), player.getName().getString());
        var key = target.key();
        return teleportToTarget(player, target).whenComplete((success, error) ->
                PlayerActionLog.log(actor, "remote_tp_arrival",
                        error == null && Boolean.TRUE.equals(success) ? "success" : "failed",
                        "server", key.serverId().value(), "dimension", key.dimensionName(),
                        "list", key.listName(), "waypoint", key.waypointName()));
    }

    private CompletionStage<Boolean> teleportToTarget(ServerPlayer player, DestinationResolver.Target target) {
        if (!ownsThread(player) || !isCurrentPlayer(player)) return CompletableFuture.completedFuture(false);
        for (var level : server.getAllLevels()) {
            //? if >=1.21.11 {
            String dimension = level.dimension().identifier().toString();
            //?} else {
            /*String dimension = level.dimension().location().toString();
            *///?}
            if (!dimension.equals(target.key().dimensionName())) continue;
            var pos = target.position();
            //? if >=1.21.2 {
            return CompletableFuture.completedFuture(player.teleportTo(level, pos.x() + 0.5D, pos.y(), pos.z() + 0.5D,
                    Set.of(), target.yaw(), 0, false));
            //?} else {
            /*return CompletableFuture.completedFuture(player.teleportTo(level, pos.x() + 0.5D, pos.y(), pos.z() + 0.5D,
                    Set.of(), target.yaw(), 0));
            *///?}
        }
        return CompletableFuture.completedFuture(false);
    }
}
