package _959.server_waypoint.fabric.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class FabricPermissionManager extends PermissionManager<CommandSourceStack, String, ServerPlayer> {
    private static boolean isFabricPermissionAPILoaded = false;

    public FabricPermissionManager() {
        super(new PermissionStringKeys());
    }

    public static void setFabricPermissionAPILoaded(boolean flag) {
        FabricPermissionManager.isFabricPermissionAPILoaded = flag;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        if (isFabricPermissionAPILoaded) {
            return Permissions.check(source, key.getKey(), defaultLevel);
        } else {
            return source.hasPermission(defaultLevel);
        }
    }

    @Override
    public boolean checkPlayerPermission(ServerPlayer player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        if (isFabricPermissionAPILoaded) {
            return Permissions.check(player, key.getKey(), defaultLevel);
        } else {
            return player.hasPermissions(defaultLevel);
        }
    }
}
