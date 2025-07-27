package _959.server_waypoint.neoforge.permission;

import _959.server_waypoint.common.permission.PermissionKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.server.permission.PermissionAPI;

public class NeoForgePermissionManager {
    public static boolean hasPermission(ServerCommandSource source, PermissionKey permission, int defaultRequiredLevel) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return source.hasPermissionLevel(defaultRequiredLevel);
        } else {
            return PermissionAPI.getPermission(player, permission.getNode());
        }
    }
}