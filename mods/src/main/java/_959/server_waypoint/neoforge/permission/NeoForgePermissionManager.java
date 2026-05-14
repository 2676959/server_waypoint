package _959.server_waypoint.neoforge.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class NeoForgePermissionManager extends PermissionManager<CommandSourceStack, String, ServerPlayer> {
    public NeoForgePermissionManager() {
        super(new PermissionStringKeys());
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return source.hasPermission(defaultLevel);
        } else {
            return checkPlayerPermission(player, key, defaultLevel);
        }
    }

    @Override
    public boolean checkPlayerPermission(ServerPlayer player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        return player.hasPermissions(defaultLevel);
    }
}
