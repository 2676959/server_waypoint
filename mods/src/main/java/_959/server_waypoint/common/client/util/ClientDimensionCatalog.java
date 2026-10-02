package _959.server_waypoint.common.client.util;

import _959.server_waypoint.common.client.WaypointClientMod;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.getCommandSuggestions;
import static _959.server_waypoint.util.VanillaDimensionNames.dimensionNameComparator;

/** Resolves the complete dimension catalog available to client GUI screens. */
public final class ClientDimensionCatalog {
    private ClientDimensionCatalog() {
    }

    public static CompletableFuture<List<String>> getAvailableDimensionNames() {
        var integratedServer = Minecraft.getInstance().getSingleplayerServer();
        if (integratedServer != null) {
            return CompletableFuture.completedFuture(sortDimensionNames(
                    integratedServer.levelKeys().stream().map(key ->
                            //? if >= 1.21.11 {
                            key.identifier().toString()
                            //?} else {
                            /*key.location().toString()
                            *///?}
                    ).toList()
            ));
        }
        return getCommandSuggestions("wp list ").handle((suggestions, exception) ->
                mergeWithCachedDimensions(exception == null
                        ? dimensionNamesFromSuggestions(suggestions)
                        : List.of())
        );
    }

    public static List<String> mergeWithCachedDimensions(List<String> dimensionNames) {
        return mergeDimensionNames(dimensionNames, WaypointClientMod.getAllAvailableDimensionNames());
    }

    public static List<String> mergeDimensionNames(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return sortDimensionNames(List.copyOf(merged));
    }

    static List<String> dimensionNamesFromSuggestions(List<String> suggestions) {
        return sortDimensionNames(suggestions.stream()
                .filter(suggestion -> {
                    int separator = suggestion.indexOf(':');
                    return separator > 0 && separator < suggestion.length() - 1;
                })
                .toList());
    }

    private static List<String> sortDimensionNames(List<String> dimensionNames) {
        List<String> sorted = new ArrayList<>(new LinkedHashSet<>(dimensionNames));
        sorted.sort(ClientDimensionCatalog::compareDimensionNames);
        return List.copyOf(sorted);
    }

    private static int compareDimensionNames(String first, String second) {
        return dimensionNameComparator(first, second);
    }
}
