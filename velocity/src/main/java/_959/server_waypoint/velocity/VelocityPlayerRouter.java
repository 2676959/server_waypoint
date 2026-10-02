package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.proxy.*;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.*;
import java.util.concurrent.*;

/** Stable configured IDs, current proxy UUID/source checks, and the real asynchronous Velocity switch. */
public final class VelocityPlayerRouter implements ProxyPlayerRouter<RemoteServerId>, ProxyServerDirectory<RegisteredServer>, ProxyPermissionChecker {
    private final ProxyServer proxy;
    private final Map<RemoteServerId, String> mappings;
    private final String permission;
    public VelocityPlayerRouter(ProxyServer proxy, Map<RemoteServerId, String> mappings, String permission) {
        this.proxy = Objects.requireNonNull(proxy); this.mappings = Map.copyOf(mappings); this.permission = Objects.requireNonNull(permission);
        if (new HashSet<>(mappings.values()).size() != mappings.size()) throw new IllegalArgumentException("Duplicate Velocity server mapping");
    }
    @Override public Optional<RegisteredServer> findServer(RemoteServerId id) {
        String name = mappings.get(id); return name == null ? Optional.empty() : proxy.getServer(name);
    }
    @Override public Optional<ProxyPlayerSnapshot> findPlayer(UUID id) {
        return proxy.getPlayer(id).filter(player -> player.isActive() && player.getUniqueId().equals(id)).map(player ->
                new ProxyPlayerSnapshot(player.getUniqueId(), player.getCurrentServer().flatMap(connection -> mappings.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(connection.getServerInfo().getName())).map(Map.Entry::getKey).findFirst())));
    }
    @Override public boolean hasPermission(UUID id, String node) {
        return proxy.getPlayer(id).filter(player -> player.isActive() && player.getUniqueId().equals(id))
                .map(player -> node.isEmpty() || player.hasPermission(node)).orElse(false);
    }
    public boolean allowed(UUID id) { return hasPermission(id, permission); }
    @Override public CompletionStage<TransferResult> transfer(UUID id, RemoteServerId source, RemoteServerId target) {
        var player = proxy.getPlayer(id).orElse(null);
        if (player == null || !player.isActive() || !id.equals(player.getUniqueId())) return completed(TransferResult.PLAYER_OFFLINE);
        var destination = findServer(target).orElse(null);
        if (destination == null) return completed(TransferResult.UNKNOWN_DESTINATION);
        if (!allowed(id)) return completed(TransferResult.PERMISSION_DENIED);
        // Last current-route observation immediately before connect(), never a cached backend assertion.
        if (!player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).filter(mappings.getOrDefault(source, "")::equals).isPresent()) {
            return completed(TransferResult.SOURCE_MISMATCH);
        }
        return player.createConnectionRequest(destination).connect().handle((result, failure) ->
                failure == null && result != null && result.isSuccessful() ? TransferResult.SUCCESS : TransferResult.CONNECTION_FAILED);
    }
    private static CompletionStage<TransferResult> completed(TransferResult result) { return CompletableFuture.completedFuture(result); }
}
