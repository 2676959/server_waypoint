package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.transport.TransportResult;
import _959.server_waypoint.crossserver.transport.TransportMode;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CrossServerStatusCommand implements SimpleCommand {
    static final String PERMISSION = "server_waypoint.command.cross_server.status";
    private final VelocityRuntime runtime;

    CrossServerStatusCommand(VelocityRuntime runtime) { this.runtime = runtime; }

    @Override public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof ConsoleCommandSource || invocation.source().hasPermission(PERMISSION);
    }

    @Override public void execute(Invocation invocation) {
        if (invocation.arguments().length != 1 || !invocation.arguments()[0].equalsIgnoreCase("status")) {
            invocation.source().sendMessage(Component.text("Usage: /serverwaypoint status"));
            return;
        }
        for (String line : lines(runtime.status())) invocation.source().sendMessage(Component.text(line));
    }

    static List<String> lines(VelocityRuntime.Status status) {
        List<String> lines = new ArrayList<>();
        TransportResult result = status.startupResult();
        String state = status.stopping() ? "stopped" : result == null ? "starting"
                : result == TransportResult.DISABLED ? "disabled"
                : result == TransportResult.INVALID_CONFIGURATION ? "invalid configuration"
                : result == TransportResult.UNAVAILABLE ? "unavailable"
                : status.coordinator() != null && status.coordinator().running() ? "running" : "unavailable";
        lines.add("Server Waypoint cross-server: " + state);
        TransportMode mode = status.transportMode();
        lines.add("Transport mode: " + (mode == null ? "inactive" : mode == TransportMode.NOISE_KK
                ? "NOISE_KK (encrypted)" : "PLAINTEXT (unencrypted)"));
        var coordinator = status.coordinator();
        if (coordinator != null && coordinator.running() && !status.stopping()) {
            lines.add("Coordinator listening on port " + coordinator.port());
        }
        var onlineIds = coordinator != null && coordinator.running() && !status.stopping()
                ? coordinator.backends().keySet() : Set.<RemoteServerId>of();
        List<String> online = status.configuredBackends().stream().filter(onlineIds::contains)
                .map(id -> id.value()).sorted().toList();
        List<String> offline = status.configuredBackends().stream().filter(id -> !onlineIds.contains(id))
                .map(id -> id.value()).sorted().toList();
        lines.add("Online servers (" + online.size() + "): " + (online.isEmpty() ? "none" : String.join(", ", online)));
        lines.add("Offline servers (" + offline.size() + "): " + (offline.isEmpty() ? "none" : String.join(", ", offline)));
        if (result == TransportResult.INVALID_CONFIGURATION || result == TransportResult.UNAVAILABLE) {
            if (status.failureDetails() != null && !status.failureDetails().isBlank()) {
                lines.add("Startup error: " + status.failureDetails());
            }
        }
        return List.copyOf(lines);
    }
}
