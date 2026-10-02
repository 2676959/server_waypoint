package _959.server_waypoint.command.permission;


public abstract class PermissionManager<S, K, P> {
    public final PermissionKeys<K> keys;
    public abstract boolean hasPermission(S source, PermissionKeys<K>.PermissionKey key, int defaultLevel);
    public abstract boolean checkPlayerPermission(P player, PermissionKeys<K>.PermissionKey key, int defaultLevel);
    /** Offline lookup; vanilla-only adapters use the destination's supplied operator-level fallback. */
    public java.util.concurrent.CompletionStage<Boolean> checkOfflinePermission(java.util.UUID playerId,
            PermissionKeys<K>.PermissionKey key, boolean fallback) {
        return java.util.concurrent.CompletableFuture.completedFuture(fallback);
    }
    public PermissionManager(PermissionKeys<K> keys) {
        this.keys = keys;
    }
}
