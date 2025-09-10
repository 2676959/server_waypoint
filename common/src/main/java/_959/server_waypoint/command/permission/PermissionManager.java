package _959.server_waypoint.command.permission;


public abstract class PermissionManager<S, K, P> {
    public final PermissionKeys<K> keys;
    public abstract boolean hasPermission(S source, PermissionKeys<K>.PermissionKey key, int defaultLevel);
    public abstract boolean checkPlayerPermission(P player, PermissionKeys<K>.PermissionKey key, int defaultLevel);
    public PermissionManager(PermissionKeys<K> keys) {
        this.keys = keys;
    }
}
