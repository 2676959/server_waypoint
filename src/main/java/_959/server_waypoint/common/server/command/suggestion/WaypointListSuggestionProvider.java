package _959.server_waypoint.common.server.command.suggestion;

import _959.server_waypoint.common.server.WaypointServer;
import _959.server_waypoint.common.server.waypoint.WaypointFileManager;
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
        WaypointFileManager waypointFileManager = waypointServer.getDimensionManager(dimKey);
        if (waypointFileManager == null) {
            return Suggestions.empty();
        } else {
            for (String listName : waypointFileManager.getWaypointListMap().keySet()) {
                builder.suggest(listName);
            }
        }
        return builder.buildFuture();
    }
}
