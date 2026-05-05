package _959.server_waypoint.common.server.command;

import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;

import com.mojang.brigadier.Message;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

//? if >= 1.21.2
import java.util.Collections;

public class WaypointCommand extends CoreWaypointCommand<CommandSourceStack, String, ServerPlayer, Identifier, Coordinates> {
    public WaypointCommand(WaypointServerMod waypointServer, PlatformMessageSender<CommandSourceStack, ServerPlayer> networkAdapter, PermissionManager<CommandSourceStack, String, ServerPlayer> permissionManager) {
        super(waypointServer, networkAdapter, permissionManager, DimensionArgument::dimension, BlockPosArgument::blockPos);
    }

    @Nullable
    private ServerLevel getWorldFromId(CommandSourceStack source, Identifier id) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, id);
        return source.getServer().getLevel(dimKey);
    }

    @Override
    protected String toDimensionName(Identifier dimensionArgument) {
        return dimensionArgument.toString();
    }

    @Override
    protected WaypointPos toWaypointPos(CommandSourceStack source, Coordinates blockPositionArgument) {
        BlockPos blockPos = blockPositionArgument.getBlockPos(source);
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    @Override
    protected boolean isDimensionValid(CommandSourceStack source, Identifier dimensionArgument) {
        return getWorldFromId(source, dimensionArgument) != null;
    }

    @Override
    protected void executeByServer(CommandSourceStack source, Runnable task) {
        source.getServer().execute(task);
    }

    @Override
    protected Identifier getSourceDimension(CommandSourceStack source) {
        return source.getLevel().dimension().identifier();
    }

    @Override
    protected float getSourceYaw(CommandSourceStack source) {
        Entity entity;
        if ((entity = source.getEntity()) != null) {
            return entity.getYRot();
        }
        return 0F;
    }

    @Nullable
    @Override
    protected ServerPlayer getPlayer(CommandSourceStack source) {
        return source.getPlayer();
    }

    @Override
    protected String getPlayerName(ServerPlayer player) {
        return player.getName().getString();
    }

    @Override
    protected void teleportPlayer(CommandSourceStack source, ServerPlayer player, Identifier dimensionArgument, WaypointPos pos, int yaw) {
        ServerLevel world = getWorldFromId(source, dimensionArgument);
        //? if >= 1.21.2 {
        player.teleportTo(world, pos.X(), pos.y(), pos.Z(), Collections.emptySet(), yaw, 0, false);
        //?} else {
        /*player.teleportTo(world, pos.X(), pos.y(), pos.Z(), yaw, 0);
        *///?}
    }

    @Override
    protected Message getMessageFromComponent(Component component) {
        return ModMessageSender.toVanillaText(component);
    }
}
