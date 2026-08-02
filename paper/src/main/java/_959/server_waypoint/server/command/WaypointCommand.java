package _959.server_waypoint.server.command;

import _959.server_waypoint.ServerWaypointPaperMC;
import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationService;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class WaypointCommand extends CoreWaypointCommand<CommandSourceStack, String, Player, World, BlockPositionResolver> {
    private final PaperScheduler scheduler;

    public WaypointCommand(
            WaypointServerCore waypointServer,
            PlatformMessageSender<CommandSourceStack, Player> sender,
            PermissionManager<CommandSourceStack, String, Player> permissionManager,
            NavigationService<Player> navigationService
    ) {
        super(
                waypointServer,
                sender,
                permissionManager,
                navigationService,
                ArgumentTypes::world,
                ArgumentTypes::blockPosition
        );
        this.scheduler = new PaperScheduler(ServerWaypointPaperMC.getSelf());
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
    protected boolean isDimensionValid(CommandSourceStack source, World dimensionArgument) {
        return true;
    }

    @Override
    protected void executeByServer(CommandSourceStack source, Runnable task) {
        this.scheduler.runAsyncDelayed(task, 20L);
    }

    @Override
    protected World getSourceDimension(CommandSourceStack source) {
        return source.getLocation().getWorld();
    }

    @Override
    protected WaypointPos getSourcePosition(CommandSourceStack source) {
        Location location = source.getLocation();
        return new WaypointPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
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
        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    @Override
    protected Message getMessageFromComponent(Component component) {
        return MessageComponentSerializer.message().serialize(component);
    }
}
