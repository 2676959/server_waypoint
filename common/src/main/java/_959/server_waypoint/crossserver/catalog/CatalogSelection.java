package _959.server_waypoint.crossserver.catalog;

import java.util.*;

/** Immutable, player-independent PUBLIC export selection. An empty selection exports an explicit empty catalog. */
public record CatalogSelection(boolean all, Map<String, Set<String>> lists) {
    public CatalogSelection {
        Map<String, Set<String>> copy = new HashMap<>(); lists.forEach((dimension, names) -> copy.put(dimension, Set.copyOf(names)));
        lists = Map.copyOf(copy);
        if (all && !lists.isEmpty()) throw new IllegalArgumentException("Ambiguous export selection");
    }
    public static CatalogSelection allPublic() { return new CatalogSelection(true, Map.of()); }
    public boolean includes(String dimension, String list) { return all || lists.getOrDefault(dimension, Set.of()).contains(list); }
    public boolean includesDimension(String dimension) { return all || lists.containsKey(dimension); }
}
