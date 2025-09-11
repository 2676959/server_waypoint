package _959.server_waypoint.command.permission;

import java.util.Objects;

import static _959.server_waypoint.core.WaypointServerCore.GROUP_ID;

public class PermissionStringKeys extends PermissionKeys<String> {
    public PermissionStringKeys() {
        super();
    }

    private PermissionKeys<String>.PermissionKey build(String permission) {
        return new PermissionKey(GROUP_ID + "." + permission);
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createAddPermissionKey() {
        return build("command.add");
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createEditPermissionKey() {
        return build("command.edit");
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createRemovePermissionKey() {
        return build("command.remove");
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createTpPermissionKey() {
        return build("command.tp");
    }

    @Override
    protected PermissionKeys<String>.PermissionKey createReloadPermissionKey() {
        return build("command.reload");
    }
}