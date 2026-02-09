package _959.server_waypoint.common.server.command;

import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;

import com.mojang.brigadier.Message;
import net.kyori.adventure.text.Component;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

//? if >= 1.21.2
import java.util.Collections;

public class WaypointCommand extends CoreWaypointCommand<ServerCommandSource, String, ServerPlayerEntity, Identifier, PosArgument> {
    public WaypointCommand(WaypointServerMod waypointServer, PlatformMessageSender<ServerCommandSource, ServerPlayerEntity> networkAdapter, PermissionManager<ServerCommandSource, String, ServerPlayerEntity> permissionManager) {
        super(waypointServer, networkAdapter, permissionManager, DimensionArgumentType::dimension, BlockPosArgumentType::blockPos);
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
        //? if >= 1.21.2 {
        player.teleport(world, pos.X(), pos.y(), pos.Z(), Collections.emptySet(), yaw, 0, false);
        //?} else {
        /*player.teleport(world, pos.X(), pos.y(), pos.Z(), yaw, 0);
        *///?}
    }

    @Override
    protected Message getMessageFromComponent(Component component) {
        return ModMessageSender.toVanillaText(component);
    }
}
