package _959.server_waypoint.common.server.handoff;

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
import java.util.function.Predicate;

/** Server-bound adapter shared by Fabric, Forge and NeoForge. Lifecycle wiring remains separate. */
public final class ModDestinationPlatform implements DestinationPlatform<ServerPlayer> {
    private final MinecraftServer server;
    private final Predicate<ServerPlayer> permission;

    public ModDestinationPlatform(MinecraftServer server, Predicate<ServerPlayer> permission) {
        this.server = Objects.requireNonNull(server);
        this.permission = Objects.requireNonNull(permission);
    }
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
    @Override public boolean canTeleport(ServerPlayer player) { return permission.test(player); }
    @Override public CompletionStage<Boolean> teleport(ServerPlayer player, DestinationResolver.Target target) {
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
