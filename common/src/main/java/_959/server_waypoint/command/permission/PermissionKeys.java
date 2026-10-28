package _959.server_waypoint.command.permission;

public abstract class PermissionKeys<K> {
    protected final PermissionKey privateList;
    protected final PermissionKey privateAdd;
    protected final PermissionKey privateEdit;
    protected final PermissionKey privateRemove;
    protected final PermissionKey privateNavigate;
    protected final PermissionKey privateTp;
    protected final PermissionKey add;
    protected final PermissionKey edit;
    protected final PermissionKey remove;
    protected final PermissionKey navigate;
    protected final PermissionKey tp;
    protected final PermissionKey reload;
    protected final PermissionKey upload;
    protected final PermissionKey uploadDelete;
    protected final PermissionKey remoteList;
    protected final PermissionKey remoteTp;

    protected abstract PermissionKey createAddPermissionKey();
    protected abstract PermissionKey createEditPermissionKey();
    protected abstract PermissionKey createRemovePermissionKey();
    protected abstract PermissionKey createNavigatePermissionKey();
    protected abstract PermissionKey createTpPermissionKey();
    protected abstract PermissionKey createReloadPermissionKey();
    protected abstract PermissionKey createUploadPermissionKey();
    protected abstract PermissionKey createUploadDeletePermissionKey();

    protected abstract PermissionKey createRemoteListPermissionKey();
    protected abstract PermissionKey createRemoteTpPermissionKey();

    protected PermissionKeys() {
        this.privateList = createPrivatePermissionKey("list");
        this.privateAdd = createPrivatePermissionKey("add");
        this.privateEdit = createPrivatePermissionKey("edit");
        this.privateRemove = createPrivatePermissionKey("remove");
        this.privateNavigate = createPrivatePermissionKey("navigate");
        this.privateTp = createPrivatePermissionKey("tp");
        this.add = createAddPermissionKey();
        this.edit = createEditPermissionKey();
        this.remove = createRemovePermissionKey();
        this.navigate = createNavigatePermissionKey();
        this.tp = createTpPermissionKey();
        this.reload = createReloadPermissionKey();
        this.upload = createUploadPermissionKey();
        this.uploadDelete = createUploadDeletePermissionKey();
        this.remoteList = createRemoteListPermissionKey();
        this.remoteTp = createRemoteTpPermissionKey();
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

    public PermissionKey navigate() {
        return this.navigate;
    }

    public PermissionKey reload() {
        return this.reload;
    }

    public PermissionKey tp() {
        return this.tp;
    }

    public PermissionKey upload() {
        return this.upload;
    }

    public PermissionKey uploadDelete() {
        return this.uploadDelete;
    }

    public PermissionKey remoteList() {
        return this.remoteList;
    }

    public PermissionKey remoteTp() {
        return this.remoteTp;
    }

    protected abstract PermissionKey createPrivatePermissionKey(String action);

    public PermissionKey privateList() {
        return this.privateList;
    }

    public PermissionKey privateAdd() {
        return this.privateAdd;
    }

    public PermissionKey privateEdit() {
        return this.privateEdit;
    }

    public PermissionKey privateRemove() {
        return this.privateRemove;
    }

    public PermissionKey privateNavigate() {
        return this.privateNavigate;
    }

    public PermissionKey privateTp() {
        return this.privateTp;
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
