package _959.server_waypoint.command.permission;

public abstract class PermissionKeys<K> {
    protected final PermissionKey add;
    protected final PermissionKey edit;
    protected final PermissionKey remove;
    protected final PermissionKey tp;
    protected final PermissionKey reload;

    protected abstract PermissionKey createAddPermissionKey();
    protected abstract PermissionKey createEditPermissionKey();
    protected abstract PermissionKey createRemovePermissionKey();
    protected abstract PermissionKey createTpPermissionKey();
    protected abstract PermissionKey createReloadPermissionKey();

    protected PermissionKeys() {
        this.add = createAddPermissionKey();
        this.edit = createEditPermissionKey();
        this.remove = createRemovePermissionKey();
        this.tp = createTpPermissionKey();
        this.reload = createReloadPermissionKey();
    }

    public PermissionKey add() {
        return this.add;
    }

    public PermissionKey edit() {
        return this.edit;
    }

    public PermissionKey remove() {
        return this.remove;
    }

    public PermissionKey reload() {
        return this.reload;
    }

    public PermissionKey tp() {
        return this.tp;
    }

    public class PermissionKey {
        private final K permissionKey;

        public K getKey() {
            return permissionKey;
        };

        public PermissionKey(K key) {
            permissionKey = key;
        }
    }
}
