package _959.server_waypoint.fabric.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import _959.server_waypoint.common.permission.PermissionKey;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.audience.Audience;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;

public class FabricPermissionManager extends PermissionManager<ServerCommandSource, String, ServerPlayerEntity> {
    private static boolean isFabricPermissionAPILoaded = false;

    public FabricPermissionManager() {
        super(new PermissionStringKeys());
    }

    public static void setFabricPermissionAPILoaded(boolean flag) {
        FabricPermissionManager.isFabricPermissionAPILoaded = flag;
    }

    @Override
    public boolean hasPermission(ServerCommandSource source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        if (isFabricPermissionAPILoaded) {
            return Permissions.check(source, key.getKey(), defaultLevel);
        } else {
            return source.hasPermissionLevel(defaultLevel);
        }
    }

    @Override
    public boolean checkPlayerPermission(ServerPlayerEntity player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        if (isFabricPermissionAPILoaded) {
            return Permissions.check(player, key.getKey(), defaultLevel);
        } else {
            return player.hasPermissionLevel(defaultLevel);
        }
    }
}
