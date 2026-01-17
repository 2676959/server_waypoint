package _959.server_waypoint.util;

import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MapUtils {
    /**
     * Returns an immutable sorted List of entries sorted by the Key.
     * Requires Keys to implement Comparable (e.g., String, Integer).
     */
    public static <K extends Comparable<? super K>, V> @Unmodifiable List<Map.Entry<K, V>> getEntriesSortedByKey(Map<K, V> map, int offset) {
        int size = map.size();
        if (size <= offset) {
            return map.entrySet().stream().toList();
        } else {
            List<Map.Entry<K, V>> sortedList = new ArrayList<>(map.entrySet());
            return sortedList.subList(offset, size).stream().sorted().toList();
        }
    }
}
