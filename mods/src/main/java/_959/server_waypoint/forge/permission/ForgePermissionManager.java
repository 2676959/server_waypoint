package _959.server_waypoint.forge.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import net.minecraft.commands.CommandSourceStack;
//? if >= 1.21.11 {
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
//?}
import net.minecraft.server.level.ServerPlayer;

public class ForgePermissionManager extends PermissionManager<CommandSourceStack, String, ServerPlayer> {
    public ForgePermissionManager() {
        super(new PermissionStringKeys());
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            //? if >= 1.21.11 {
            return hasPermissionLevel(source.permissions(), defaultLevel);
            //?} else {
            /*return source.hasPermission(defaultLevel);
            *///?}
        } else {
            return checkPlayerPermission(player, key, defaultLevel);
        }
    }

    @Override
    public boolean checkPlayerPermission(ServerPlayer player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        //? if >= 1.21.11 {
        return hasPermissionLevel(player.permissions(), defaultLevel);
        //?} else {
        /*return player.hasPermissions(defaultLevel);
        *///?}
    }

    //? if >= 1.21.11 {
    private static boolean hasPermissionLevel(PermissionSet permissions, int defaultLevel) {
        if (defaultLevel <= 0) {
            return true;
        }
        if (permissions instanceof net.minecraft.server.permissions.LevelBasedPermissionSet levelBasedPermissions) {
            return levelBasedPermissions.level().isEqualOrHigherThan(PermissionLevel.byId(defaultLevel));
        }
        return false;
    }
    //?}
}
