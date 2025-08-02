package _959.server_waypoint.common.server.command.suggestion;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.common.server.WaypointServerMod;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

import java.util.concurrent.CompletableFuture;

import static _959.server_waypoint.common.util.DimensionFileHelper.getFileName;
import static net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument;


public class WaypointListSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        WaypointServerMod waypointServerMod = WaypointServerMod.INSTANCE;
        RegistryKey<World> dimKey;
        try {
            dimKey = getDimensionArgument(context, "dimension").getRegistryKey();
        } catch (Exception e) {
            dimKey = context.getSource().getWorld().getRegistryKey();
        }
        WaypointFileManager waypointFileManager = waypointServerMod.getWaypointFileManager(getFileName(dimKey));
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
