package _959.server_waypoint.fabric.server.command.suggestion;

import _959.server_waypoint.fabric.server.WaypointServer;
import _959.server_waypoint.fabric.server.waypoint.DimensionManager;
import _959.server_waypoint.fabric.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.fabric.server.waypoint.WaypointList;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument;

public class WaypointNameSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        RegistryKey<World> dimKey;
        try {
            dimKey = getDimensionArgument(context, "dimension").getRegistryKey();
        } catch (Exception e) {
            dimKey = context.getSource().getWorld().getRegistryKey();
        }
        DimensionManager dimensionManager = WaypointServer.INSTANCE.getDimensionManager(dimKey);
        if (dimensionManager == null) {
            return Suggestions.empty();
        }
        WaypointList waypointList = dimensionManager.getWaypointListByName(getString(context, "list"));
        if (waypointList == null) {
            return Suggestions.empty();
        } else {
            for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                builder.suggest(waypoint.name());
            }
            return builder.buildFuture();
        }
    }
}
