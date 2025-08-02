package _959.server_waypoint.common.permission;

//? if neoforge {
/*import _959.server_waypoint.config.CommandPermission;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import static _959.server_waypoint.common.server.WaypointServer.CONFIG;
*///?}

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;


public enum PermissionKey {
    //? if neoforge {
    /*COMMAND_ADD("command.add", (player, uuid, context) -> player != null && player.hasPermissionLevel(CONFIG.CommandPermission().add())),
    COMMAND_EDIT("command.edit", (player, uuid, context) -> player != null && player.hasPermissionLevel(CONFIG.CommandPermission().edit())),
    COMMAND_REMOVE("command.remove",  (player, uuid, context) -> player != null && player.hasPermissionLevel(CONFIG.CommandPermission().remove()));
    *///?} elif fabric {
    COMMAND_ADD("command.add"),
    COMMAND_EDIT("command.edit"),
    COMMAND_REMOVE("command.remove");
    //?}

    private final String nodeString;
    //? if neoforge
    /*private final PermissionNode<Boolean> node;*/

    PermissionKey(String permission/*? if neoforge {*//*, PermissionNode.PermissionResolver<Boolean> resolver*//*?}*/) {
        this.nodeString = permission;
        //? if neoforge
        /*this.node = new PermissionNode<>(MOD_ID, permission, PermissionTypes.BOOLEAN, resolver);*/
    }

    //? if neoforge
    /*public PermissionNode<Boolean> getNode() { return this.node; }*/

    @Override public String toString() {
        return GROUP_ID + "." + nodeString;
    }
}
