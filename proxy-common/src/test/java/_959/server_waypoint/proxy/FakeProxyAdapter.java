package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Deterministic, in-memory adapter fixture. No network, proxy API, or teleport implementation. */
final class FakeProxyAdapter implements ProxyPlayerRouter<FakeProxyAdapter.ServerHandle>,
        ProxyServerDirectory<FakeProxyAdapter.ServerHandle>, ProxyPermissionChecker, ProxyLifecycle {
    static final String TRANSFER_PERMISSION = "server_waypoint.command.remote.tp";
    record ServerHandle(UUID registration) { }

    private final Map<RemoteServerId, ServerHandle> servers = new HashMap<>();
    private final Map<UUID, RemoteServerId> players = new HashMap<>();
    private final Map<UUID, Set<String>> permissions = new HashMap<>();
    private final List<Runnable> startCallbacks = new ArrayList<>();
    private final List<Runnable> stopCallbacks = new ArrayList<>();
    private final List<Consumer<UUID>> disconnectCallbacks = new ArrayList<>();
    private final List<PendingTask> tasks = new ArrayList<>();
    private final Set<CompletableFuture<TransferResult>> transfers = new HashSet<>();
    private Duration now = Duration.ZERO;
    private boolean running;
    private TransferResult nextConnectionResult = TransferResult.SUCCESS;

    ServerHandle register(RemoteServerId id) {
        ServerHandle handle = new ServerHandle(UUID.randomUUID());
        if (servers.putIfAbsent(id, handle) != null) {
            throw new IllegalArgumentException("Duplicate server ID");
        }
        return handle;
    }

    void unregister(RemoteServerId id) {
        servers.remove(id);
    }

    void connect(UUID player, RemoteServerId source) {
        players.put(player, source);
    }

    void disconnect(UUID player) {
        players.remove(player);
        permissions.remove(player);
        List.copyOf(disconnectCallbacks).forEach(callback -> callback.accept(player));
    }

    void grant(UUID player, String permission) {
        permissions.computeIfAbsent(player, ignored -> new HashSet<>()).add(permission);
    }

    void revoke(UUID player, String permission) {
        Set<String> granted = permissions.get(player);
        if (granted != null) {
            granted.remove(permission);
        }
    }

    void failNextConnection() {
        nextConnectionResult = TransferResult.CONNECTION_FAILED;
    }

    @Override
    public Optional<ServerHandle> findServer(RemoteServerId id) {
        return Optional.ofNullable(servers.get(id));
    }

    @Override
    public Optional<ProxyPlayerSnapshot> findPlayer(UUID playerId) {
        return players.containsKey(playerId)
                ? Optional.of(new ProxyPlayerSnapshot(playerId, Optional.ofNullable(players.get(playerId))))
                : Optional.empty();
    }

    @Override
    public boolean hasPermission(UUID playerId, String permission) {
        return players.containsKey(playerId) && permissions.getOrDefault(playerId, Set.of()).contains(permission);
    }

    @Override
    public CompletionStage<TransferResult> transfer(UUID playerId, RemoteServerId expectedSource, ServerHandle destination) {
        if (!running) {
            return CompletableFuture.completedFuture(TransferResult.CANCELLED);
        }
        var result = new CompletableFuture<TransferResult>();
        transfers.add(result);
        schedule(Duration.ZERO, () -> {
            TransferResult outcome;
            RemoteServerId target = servers.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(destination)).map(Map.Entry::getKey).findFirst().orElse(null);
            if (!players.containsKey(playerId)) {
                outcome = TransferResult.PLAYER_OFFLINE;
            } else if (!expectedSource.equals(players.get(playerId))) {
                outcome = TransferResult.SOURCE_MISMATCH;
            } else if (target == null) {
                outcome = TransferResult.UNKNOWN_DESTINATION;
            } else if (!hasPermission(playerId, TRANSFER_PERMISSION)) {
                outcome = TransferResult.PERMISSION_DENIED;
            } else {
                outcome = nextConnectionResult;
                nextConnectionResult = TransferResult.SUCCESS;
                if (outcome == TransferResult.SUCCESS) {
                    players.put(playerId, target);
                }
            }
            transfers.remove(result);
            result.complete(outcome);
        });
        return result;
    }

    @Override
    public Registration onStart(Runnable callback) {
        return registerCallback(startCallbacks, callback);
    }

    @Override
    public Registration onStop(Runnable callback) {
        return registerCallback(stopCallbacks, callback);
    }

    @Override
    public Registration onPlayerDisconnect(Consumer<UUID> callback) {
        return registerCallback(disconnectCallbacks, callback);
    }

    private static <T> Registration registerCallback(List<T> callbacks, T callback) {
        callbacks.add(callback);
        return new Registration() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    callbacks.remove(callback);
                }
            }
        };
    }

    @Override
    public Registration schedule(Duration delay, Runnable callback) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Negative delay");
        }
        if (!running) {
            throw new IllegalStateException("Stopped scheduler");
        }
        var task = new PendingTask(now.plus(delay), callback);
        tasks.add(task);
        return () -> tasks.remove(task);
    }

    void start() {
        if (!running) {
            running = true;
            List.copyOf(startCallbacks).forEach(Runnable::run);
        }
    }

    void stop() {
        if (running) {
            running = false;
            List.copyOf(stopCallbacks).forEach(Runnable::run);
            tasks.clear();
            List.copyOf(transfers).forEach(future -> future.complete(TransferResult.CANCELLED));
            transfers.clear();
        }
    }

    void advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Negative time");
        }
        now = now.plus(duration);
        for (PendingTask task : List.copyOf(tasks)) {
            if (task.when().compareTo(now) <= 0 && tasks.remove(task)) {
                task.callback().run();
            }
        }
    }

    private record PendingTask(Duration when, Runnable callback) { }
}
