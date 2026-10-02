package _959.server_waypoint.server.command.permission;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
//? if >= 1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
//?}
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class PaperPermissionManager extends PermissionManager<CommandSourceStack, String, Player> {
    public PaperPermissionManager() {
        super(new PermissionStringKeys());
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        return hasPermission(source.getSender(), key.getKey(), defaultLevel);
    }

    @Override
    public boolean checkPlayerPermission(Player player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
        String permission = key.getKey();
        if (player.isPermissionSet(permission)) {
            return player.hasPermission(permission);
        }
        return hasVanillaPermission(((CraftPlayer) player).getHandle().createCommandSourceStack(), defaultLevel);
    }

    private static boolean hasPermission(CommandSender sender, String permission, int defaultLevel) {
        if (sender.isPermissionSet(permission)) {
            return sender.hasPermission(permission);
        }
        if (sender instanceof Player player) {
            return hasVanillaPermission(((CraftPlayer) player).getHandle().createCommandSourceStack(), defaultLevel);
        }
        return hasVanillaPermission(
                ((CraftServer) sender.getServer()).getHandle().getServer().createCommandSourceStack(),
                defaultLevel
        );
    }

    private static boolean hasVanillaPermission(net.minecraft.commands.CommandSourceStack source, int defaultLevel) {
        //? if >= 1.21.11 {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(defaultLevel)));
        //?} else {
        /*return source.hasPermission(defaultLevel);
        *///?}
    }
}
