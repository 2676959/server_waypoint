package _959.server_waypoint.fabric.server.command.suggestion;

import _959.server_waypoint.fabric.server.WaypointServer;
import _959.server_waypoint.fabric.server.waypoint.DimensionManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument;


public class WaypointListSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        WaypointServer waypointServer = WaypointServer.INSTANCE;
        RegistryKey<World> dimKey;
        try {
            dimKey = getDimensionArgument(context, "dimension").getRegistryKey();
        } catch (Exception e) {
            dimKey = context.getSource().getWorld().getRegistryKey();
        }
        DimensionManager dimensionManager = waypointServer.getDimensionManager(dimKey);
        if (dimensionManager == null) {
            return Suggestions.empty();
        } else {
            for (String listName : dimensionManager.getWaypointListMap().keySet()) {
                builder.suggest(listName);
            }
        }
        return builder.buildFuture();
    }
}
