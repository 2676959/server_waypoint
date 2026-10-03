//~ resource_location_import
package _959.server_waypoint.common.server.command;

import _959.server_waypoint.common.server.LocalWaypointUpload;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.command.CoreWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.mixin.CommandSourceStackAccessor;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.common.util.ResourceLocationHelper;
import _959.server_waypoint.common.util.TextHelper;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
//? if >= 1.21.11 {
import net.minecraft.commands.arguments.IdentifierArgument;
//?} else {
/*import net.minecraft.commands.arguments.ResourceLocationArgument;
*///?}
//? if >= 1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
//?}
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class WaypointCommand extends CoreWaypointCommand<CommandSourceStack, String, ServerPlayer,
    //$ resource_location_type_swap
    Identifier
    , Coordinates,
    //$ resource_location_type_swap
    Identifier
    > {
    @Override
    protected NamespacedId toIconId(
            //$ resource_location_type_swap
            Identifier
            iconArgument) {
        return new NamespacedId(iconArgument.getNamespace(), iconArgument.getPath());
    }

    @Override
    protected CompletableFuture<Suggestions> suggestIconIds(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        var itemIds = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR) != Items.AIR);
        var voxelMapIds = Stream.concat(
                Stream.of(ResourceLocationHelper.mcId("voxelmap", "waypoint")),
                WaypointIconPolicy.voxelMapSuffixes().stream().map(suffix -> ResourceLocationHelper.mcId("voxelmap", suffix)));
        return SharedSuggestionProvider.suggestResource(Stream.concat(itemIds, voxelMapIds), builder);
    }

    public WaypointCommand(
            WaypointServerMod waypointServer,
            PlatformMessageSender<CommandSourceStack, ServerPlayer> networkAdapter,
            PermissionManager<CommandSourceStack, String, ServerPlayer> permissionManager,
            UploadCoordinator<ServerPlayer> uploadCoordinator
    ) {
        super(
                waypointServer,
                networkAdapter,
                permissionManager,
                waypointServer.navigation().service(),
                uploadCoordinator,
                DimensionArgument::dimension,
                BlockPosArgument::blockPos,
                //? if >= 1.21.11 {
                IdentifierArgument::id
                //?} else {
                /*ResourceLocationArgument::id
                *///?}
        );
    }

    @Override
    protected boolean usesLocalUpload(CommandSourceStack source, ServerPlayer player) {
        return LocalWaypointUpload.isAvailable()
                && !source.getServer().isDedicatedServer()
                && source.getServer().isSingleplayerOwner(
                        //? if >=1.21.9 {
                        new net.minecraft.server.players.NameAndId(player.getGameProfile())
                        //?} else {
                        /*player.getGameProfile()
                        *///?}
                );
    }

    @Override
    protected CompletionStage<ChunkedMessageSendResult> dispatchUpload(
            CommandSourceStack source, ServerPlayer player,
            UploadRequestBuffer request,
            Consumer<WaypointData> receiver
    ) {
        if (usesLocalUpload(source, player)) {
            return LocalWaypointUpload.dispatch(
                    source.getServer(), player, request, receiver
            );
        }
        return super.dispatchUpload(source, player, request, receiver);
    }

    @Nullable
    private ServerLevel getWorldFromId(CommandSourceStack source,
    //$ resource_location_type_swap
    Identifier
    id) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, id);
        return source.getServer().getLevel(dimKey);
    }

    @Override
    protected String toDimensionName(
    //$ resource_location_type_swap
    Identifier
    dimensionArgument) {
        return dimensionArgument.toString();
    }

    @Override
    protected WaypointPos toWaypointPos(CommandSourceStack source, Coordinates blockPositionArgument) {
        BlockPos blockPos = blockPositionArgument.getBlockPos(source);
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    @Override
    protected boolean isDimensionValid(CommandSourceStack source,
    //$ resource_location_type_swap
    Identifier
    dimensionArgument) {
        return getWorldFromId(source, dimensionArgument) != null;
    }

    @Override
    protected void executeByServer(CommandSourceStack source, Runnable task) {
        source.getServer().execute(task);
    }

    @Override
    protected
    //$ resource_location_type_swap
    Identifier
    getSourceDimension(CommandSourceStack source) {
        //? if >= 1.21.11 {
        return source.getLevel().dimension().identifier();
        //?} else {
        /*return source.getLevel().dimension().location();
        *///?}
    }

    @Override
    protected WaypointPos getSourcePosition(CommandSourceStack source) {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
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
    protected boolean isServerConsoleWithHighestPermission(CommandSourceStack source) {
        if (!source.getServer().isDedicatedServer()
                || ((CommandSourceStackAccessor) source).serverWaypoint$getSource() != source.getServer()) {
            return false;
        }
        //? if >= 1.21.11 {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(4)));
        //?} else {
        /*return source.hasPermission(4);
        *///?}
    }

    @Override
    protected String getPlayerName(ServerPlayer player) {
        return player.getName().getString();
    }

    @Override
    protected Component getPlayerDisplayName(ServerPlayer player) {
        net.minecraft.network.chat.Component displayName = player.getDisplayName();
        try {
            return TextHelper.toAdventure(displayName);
        } catch (RuntimeException exception) {
            // The name only decorates the line, so a name whose format can't be kept is said in plain text.
            WaypointServerMod.LOGGER.warn("Could not keep the format of {}'s display name", getPlayerName(player), exception);
            return Component.text(displayName.getString());
        }
    }

    @Override
    protected CompletionStage<Boolean> teleportPlayer(CommandSourceStack source, ServerPlayer player,
    //$ resource_location_type_swap
    Identifier
    dimensionArgument, WaypointPos pos, int yaw) {
        ServerLevel world = getWorldFromId(source, dimensionArgument);
        //? if >= 1.21.2 {
        return CompletableFuture.completedFuture(
                player.teleportTo(world, pos.X(), pos.y(), pos.Z(), Collections.emptySet(), yaw, 0, false));
        //?} else {
        /*return CompletableFuture.completedFuture(
                player.teleportTo(world, pos.X(), pos.y(), pos.Z(), Collections.emptySet(), yaw, 0));
        *///?}
    }

    @Override
    protected Message getMessageFromComponent(Component component) {
        return ModMessageSender.toVanillaText(component);
    }

    @Override
    protected Map<String, String> getDimensionTypes(CommandSourceStack source) {
        Map<String, String> types = new LinkedHashMap<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            //? if >= 1.21.11 {
            String dimension = level.dimension().identifier().toString();
            String type = level.dimensionTypeRegistration().unwrapKey().map(key -> key.identifier().toString()).orElse("");
            //?} else {
            /*String dimension = level.dimension().location().toString();
            String type = level.dimensionTypeRegistration().unwrapKey().map(key -> key.location().toString()).orElse("");
            *///?}
            types.put(dimension, type);
        }
        return types;
    }

    @Override
    protected List<String> getAvailableDimensionNames(CommandSourceStack source) {
        return source.getServer().levelKeys().stream().map(key ->
                //? if >= 1.21.11 {
                key.identifier().toString()
                //?} else {
                /*key.location().toString()
                *///?}
        ).toList();
    }
}
