package _959.server_waypoint.neoforge.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
//? if >= 1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
//?}

public class NeoForgePermissionManager extends PermissionManager<CommandSourceStack, String, ServerPlayer> {
    public NeoForgePermissionManager() {
        super(new PermissionStringKeys());
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            //? if >= 1.21.11 {
            return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(defaultLevel)));
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
        return player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(defaultLevel)));
        //?} else {
        /*return player.hasPermissions(defaultLevel);
        *///?}
    }
}
