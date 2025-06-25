package _959.server_waypoint.server.command.suggestion;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;

public class SuggestionProviders {
    public static final SuggestionProvider<ServerCommandSource> WAYPOINT_NAMES = new WaypointNameSuggestionProvider();
    public static final SuggestionProvider<ServerCommandSource> WAYPOINT_LIST = new WaypointListSuggestionProvider();
    public static final SuggestionProvider<ServerCommandSource> NAME_INITIALS = new NameInitialsSuggestionProvider();
    public static final SuggestionProvider<ServerCommandSource> PLAYER_YAW = new PlayerYawSuggestionProvider();
}
