package _959.server_waypoint.handoff;

import _959.server_waypoint.crossserver.handoff.*;
import _959.server_waypoint.crossserver.authorization.RemotePermissions;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.network.PaperMessageSender;
import _959.server_waypoint.server.command.WaypointCommand;
import _959.server_waypoint.server.command.permission.PaperPermissionManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static net.kyori.adventure.text.Component.translatable;

/** Paper/Folia lifecycle and owner adapters; incoming client messages never reach the TCP services. */
public final class PaperCrossServerRuntime implements Listener {
    private final BackendRuntime<CommandSourceStack, Player> runtime;
    private final PaperDestinationPlatform destination;
    private final PaperMessageSender sender;
    public PaperCrossServerRuntime(JavaPlugin plugin, WaypointServerCore manager, WaypointCommand command,
                                   PaperPermissionManager permissions, PaperMessageSender sender) {
        this.sender = sender;
        var authorization = new RemotePermissions<>(permissions, () -> CONFIG.CommandPermission(), PaperCrossServerRuntime::player);
        destination = new PaperDestinationPlatform(plugin, authorization::canTeleportOnArrival,
                id -> PaperOfflineTeleportPermission.check(plugin, id));
        runtime = new BackendRuntime<>(plugin.getDataFolder().toPath(), manager, new SourceHandoffService.Platform<>() {
            public boolean ownsThread(CommandSourceStack source) { return player(source) != null && destination.ownsThread(player(source)); }
            public UUID playerId(CommandSourceStack source) { return player(source) == null ? null : player(source).getUniqueId(); }
            public boolean isCurrentPlayer(CommandSourceStack source, UUID id) {
                var player = player(source); return player != null && id.equals(player.getUniqueId()) && destination.isCurrentPlayer(player);
            }
            public boolean canTeleport(CommandSourceStack source) { return authorization.canRequestTeleport(source); }
            public boolean execute(CommandSourceStack source, Runnable task, Runnable retired) {
                var player = player(source); return player != null && destination.execute(player, task, retired);
            }
        }, destination);
        command.setRemoteTeleportInitiator(runtime);
    }
    private static Player player(CommandSourceStack source) { return source.getExecutor() instanceof Player player ? player : null; }
    public void start() { runtime.start().thenAccept(result -> {
        String details = runtime.startupFailureDetails();
        if (details == null) WaypointServerCore.LOGGER.info("Cross-server backend startup: {}", result);
        else WaypointServerCore.LOGGER.warn("Cross-server backend startup: {}. {}", result, details);
    }); }
    public void stop() { runtime.stop(); }
    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        runtime.arrive(player.getUniqueId(), player).whenComplete((result, failure) -> {
            if (failure != null || result.result() == Result.NOT_FOUND || result.result() == Result.UNAVAILABLE) return;
            destination.execute(player, () -> sender.sendPlayerMessage(player, translatable(result.result() == Result.SUCCESS
                    ? "waypoint.remote.tp.arrived" : "waypoint.remote.tp." + result.result().name().toLowerCase(java.util.Locale.ROOT))), () -> { });
        });
    }
}
