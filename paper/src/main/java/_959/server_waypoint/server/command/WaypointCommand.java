package _959.server_waypoint.server.command;

import _959.server_waypoint.ServerWaypointPaperMC;
import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;


import static _959.server_waypoint.text.WaypointTextHelper.colorToIndex;

@SuppressWarnings("UnstableApiUsage")
public class WaypointCommand extends CoreWaypointCommand<CommandSourceStack, String, Player, World, BlockPositionResolver, NamedTextColor> {

    public WaypointCommand(PlatformMessageSender<CommandSourceStack, Player> sender, PermissionManager<CommandSourceStack, String, Player> permissionManager) {
        super(sender, permissionManager, ArgumentTypes::world, ArgumentTypes::blockPosition, ArgumentTypes::namedColor);
    }

    @Override
    protected String toDimensionName(World dimensionArgument) {
        return dimensionArgument.getKey().asString();
    }

    @Override
    protected WaypointPos toWaypointPos(CommandSourceStack source, BlockPositionResolver blockPositionArgument) {
        final BlockPosition blockPosition;
        try {
            blockPosition = blockPositionArgument.resolve(source);
        } catch (CommandSyntaxException e) {
            this.sender.sendMessage(source, e.componentMessage());
            return null;
        }
        return new WaypointPos(blockPosition.blockX(),  blockPosition.blockY(), blockPosition.blockZ());
    }

    @Override
    protected int toColorIdx(NamedTextColor colorArgument) {
        return colorToIndex(colorArgument);
    }

    @Override
    protected boolean isDimensionValid(CommandSourceStack source, World dimensionArgument) {
        return true;
    }

    @Override
    protected void executeByServer(CommandSourceStack source, Runnable task) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskLaterAsynchronously(ServerWaypointPaperMC.getSelf(), task, 20);
    }

    @Override
    protected World getSourceDimension(CommandSourceStack source) {
        return source.getLocation().getWorld();
    }

    @Override
    protected float getSourceYaw(CommandSourceStack source) {
        return source.getLocation().getYaw();
    }

    @Nullable
    @Override
    protected Player getPlayer(CommandSourceStack source) {
        Entity entity = source.getExecutor();
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }

    @Override
    protected String getPlayerName(Player player) {
        return player.getName();
    }

    @Override
    protected void teleportPlayer(CommandSourceStack source, Player player, World dimensionArgument, WaypointPos pos, int yaw) {
        Location location = new Location(dimensionArgument, pos.X(), pos.y(), pos.Z(), yaw, 0);
        player.teleport(location, PlayerTeleportEvent.TeleportCause.COMMAND);
    }
}
