package _959.server_waypoint.common.permission;

import _959.server_waypoint.ModInfo;

public enum PermissionKey {
    COMMAND_ADD("command.add"),
    COMMAND_EDIT("command.edit"),
    COMMAND_REMOVE("command.remove");

    private final String nodeString;

    PermissionKey(String permission) {
        this.nodeString = permission;
    }

    @Override public String toString() {
        return ModInfo.MOD_ID + "." + nodeString;
    }
}
