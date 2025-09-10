package _959.server_waypoint.command.permission;

import java.util.Objects;

import static _959.server_waypoint.core.WaypointServerCore.GROUP_ID;

public class PermissionStringKeys extends PermissionKeys<String> {
    public PermissionStringKeys() {
        super();
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createAddPermissionKey() {
        return Objects.requireNonNullElseGet(this.add, () -> new PermissionKey(GROUP_ID + ".command.add"));
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createEditPermissionKey() {
        return Objects.requireNonNullElseGet(this.add, () -> new PermissionKey(GROUP_ID + ".command.edit"));
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createRemovePermissionKey() {
        return Objects.requireNonNullElseGet(this.add, () -> new PermissionKey(GROUP_ID + ".command.remove"));
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createTpPermissionKey() {
        return Objects.requireNonNullElseGet(this.add, () -> new PermissionKey(GROUP_ID + ".command.tp"));
    }
}