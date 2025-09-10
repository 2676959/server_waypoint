package _959.server_waypoint.server.command.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class PaperPermissionManager extends PermissionManager<CommandSourceStack, String, Player> {
    public PaperPermissionManager() {
        super(new PermissionStringKeys());
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        String permission = key.getKey();
        if (source.getSender().isPermissionSet(permission)) {
            return source.getSender().hasPermission(permission);
        } else {
            return source.getSender().isOp();
        }
    }

    @Override
    public boolean checkPlayerPermission(Player player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        String permission = key.getKey();
        if (player.isPermissionSet(permission)) {
            return player.hasPermission(permission);
        } else {
            return player.isOp();
        }
    }
}
