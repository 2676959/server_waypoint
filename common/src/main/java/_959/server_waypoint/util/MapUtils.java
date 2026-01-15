package _959.server_waypoint.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MapUtils {
    /**
     * Returns a List of entries sorted by the Key.
     * Requires Keys to implement Comparable (e.g., String, Integer).
     */
    public static <K extends Comparable<? super K>, V> List<Map.Entry<K, V>> getEntriesSortedByKey(Map<K, V> map, int offset) {
        List<Map.Entry<K, V>> sortedList = new ArrayList<>(map.entrySet());
        sortedList.subList(offset, sortedList.size()).sort(Map.Entry.comparingByKey());
        return sortedList;
    }
}
