package _959.server_waypoint.common.server.command;

import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;

import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.Function;

public class WaypointCommand extends CoreWaypointCommand<ServerCommandSource, String, ServerPlayerEntity, Identifier, PosArgument, Formatting> {
    public WaypointCommand(PlatformMessageSender<ServerCommandSource, ServerPlayerEntity> networkAdapter, PermissionManager<ServerCommandSource, String, ServerPlayerEntity> permissionManager) {
        super(networkAdapter, permissionManager, DimensionArgumentType::dimension, BlockPosArgumentType::blockPos, ColorArgumentType::color);
    }

    @Nullable
    private ServerWorld getWorldFromId(ServerCommandSource source, Identifier id) {
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, id);
        return source.getServer().getWorld(dimKey);
    }

    @Override
    protected String toDimensionName(Identifier dimensionArgument) {
        return dimensionArgument.toString();
    }

    @Override
    protected WaypointPos toWaypointPos(ServerCommandSource source, PosArgument blockPositionArgument) {
        BlockPos blockPos = blockPositionArgument.toAbsoluteBlockPos(source);
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    @Override
    protected int toColorIdx(Formatting colorArgument) {
        return colorArgument.ordinal();
    }

    @Override
    protected boolean isDimensionValid(ServerCommandSource source, Identifier dimensionArgument) {
        return getWorldFromId(source, dimensionArgument) != null;
    }

    @Override
    protected void executeByServer(ServerCommandSource source, Runnable task) {
        source.getServer().execute(task);
    }

    @Override
    protected Identifier getSourceDimension(ServerCommandSource source) {
        return source.getWorld().getRegistryKey().getValue();
    }

    @Override
    protected float getSourceYaw(ServerCommandSource source) {
        Entity entity;
        if ((entity = source.getEntity()) != null) {
            return entity.getYaw();
        }
        return 0F;
    }

    @Nullable
    @Override
    protected ServerPlayerEntity getPlayer(ServerCommandSource source) {
        return source.getPlayer();
    }

    @Override
    protected String getPlayerName(ServerPlayerEntity player) {
        return player.getName().getString();
    }

    @Override
    protected void teleportPlayer(ServerCommandSource source, ServerPlayerEntity player, Identifier dimensionArgument, WaypointPos pos, int yaw) {
        ServerWorld world = getWorldFromId(source, dimensionArgument);
        //? if >= 1.21.3 {
        /*player.teleport(world, pos.X(), pos.y(), pos.Z(), Collections.emptySet(), yaw, 0, false);
        *///?} else {
        player.teleport(world, pos.X(), pos.y(), pos.Z(), yaw, 0);
        //?}
    }
}
