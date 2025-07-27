package _959.server_waypoint.neoforge.permission;

import net.minecraft.server.command.ServerCommandSource;

public class NeoForgePermissionManager {

    public static boolean hasPermission(ServerCommandSource source, String permission, int defaultRequiredLevel) {
        return true;
    }
}