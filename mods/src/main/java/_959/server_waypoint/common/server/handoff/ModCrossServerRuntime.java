package _959.server_waypoint.common.server.handoff;

import _959.server_waypoint.crossserver.handoff.*;
import _959.server_waypoint.crossserver.authorization.RemotePermissions;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.command.WaypointCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;
import java.util.UUID;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static net.kyori.adventure.text.Component.translatable;

/** Dedicated-server lifecycle shared by all mod loaders. No integrated-server or client control route. */
public final class ModCrossServerRuntime {
    private final Path directory;
    private final WaypointServerCore manager;
    private final WaypointCommand command;
    private final RemotePermissions<CommandSourceStack, String, ServerPlayer> authorization;
    private final PermissionManager<CommandSourceStack, String, ServerPlayer> permissions;
    private BackendRuntime<CommandSourceStack, ServerPlayer> runtime;
    private ModDestinationPlatform destination;
    public ModCrossServerRuntime(Path directory, WaypointServerCore manager, WaypointCommand command,
                                 PermissionManager<CommandSourceStack, String, ServerPlayer> permissions) {
        this.permissions = permissions;
        this.directory = directory; this.manager = manager; this.command = command;
        authorization = new RemotePermissions<>(permissions, () -> CONFIG.CommandPermission(), CommandSourceStack::getPlayer);
    }
    public void start(MinecraftServer server) {
        if (!server.isDedicatedServer()) return;
        destination = new ModDestinationPlatform(server, authorization::canTeleportOnArrival,
                new ModOfflineTeleportPermission(server, permissions)::check);
        runtime = new BackendRuntime<>(directory, manager, new SourceHandoffService.Platform<>() {
            public boolean ownsThread(CommandSourceStack source) { return server.isSameThread(); }
            public UUID playerId(CommandSourceStack source) { return source.getPlayer() == null ? null : source.getPlayer().getUUID(); }
            public boolean isCurrentPlayer(CommandSourceStack source, UUID id) {
                var player = source.getPlayer(); return player != null && id.equals(player.getUUID()) && destination.isCurrentPlayer(player);
            }
            public boolean canTeleport(CommandSourceStack source) { return authorization.canRequestTeleport(source); }
            public boolean execute(CommandSourceStack source, Runnable task, Runnable retired) {
                var player = source.getPlayer(); return player != null && destination.execute(player, task, retired);
            }
        }, destination);
        command.setRemoteTeleportInitiator(runtime);
        runtime.start().thenAccept(result -> WaypointServerCore.LOGGER.info("Cross-server backend startup: {}", result));
    }
    public void stop() { if (runtime != null) runtime.stop(); }
    public void arrived(ServerPlayer player) {
        if (runtime == null) return;
        runtime.arrive(player.getUUID(), player).whenComplete((result, failure) -> {
            if (failure != null || result.result() == Result.NOT_FOUND || result.result() == Result.UNAVAILABLE) return;
            destination.execute(player, () -> ModMessageSender.getInstance().sendPlayerMessage(player, translatable(result.result() == Result.SUCCESS
                    ? "waypoint.remote.tp.arrived" : "waypoint.remote.tp." + result.result().name().toLowerCase(java.util.Locale.ROOT))), () -> { });
        });
    }
}
