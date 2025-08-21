package _959.server_waypoint.common.server.command.suggestion;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.common.server.WaypointServerMod;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

import java.util.concurrent.CompletableFuture;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionString;
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
        WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(getDimensionString(dimKey));
        if (waypointFileManager == null) {
            return Suggestions.empty();
        }
        WaypointList waypointList = waypointFileManager.getWaypointListByName(getString(context, "list"));
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
