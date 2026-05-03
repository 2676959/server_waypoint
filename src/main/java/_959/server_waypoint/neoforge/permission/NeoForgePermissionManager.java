package _959.server_waypoint.neoforge.permission;

import _959.server_waypoint.common.permission.PermissionKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

public class NeoForgePermissionManager {
    public static boolean hasPermission(CommandSourceStack source, PermissionKey permission, int defaultRequiredLevel) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(defaultRequiredLevel);
        } else {
            return PermissionAPI.getPermission(player, permission.getNode());
        }
    }
}