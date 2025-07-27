package _959.server_waypoint.fabric.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

public class FabricPermissionManager {
    private static boolean isFabricPermissionAPILoaded = false;

    public static void setFabricPermissionAPILoaded(boolean flag) {
        FabricPermissionManager.isFabricPermissionAPILoaded = flag;
    }

    public static boolean hasPermission(ServerCommandSource source, String permission, int defaultRequiredLevel) {
        if (isFabricPermissionAPILoaded) {
            return Permissions.check(source, permission, defaultRequiredLevel);
        } else {
            return source.hasPermissionLevel(defaultRequiredLevel);
        }
    }
}
