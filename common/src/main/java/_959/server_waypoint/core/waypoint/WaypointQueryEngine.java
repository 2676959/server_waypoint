package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WaypointQueryEngine {
    private final WaypointFilesManagerCore filesManager;

    public WaypointQueryEngine(WaypointFilesManagerCore filesManager) {
        this.filesManager = Objects.requireNonNull(filesManager);
    }

    public QueryResult queryAll(Query query) {
        return queryManagers(this.filesManager.getSortedMap(), resolveQuery(query));
    }

    public QueryResult queryDimension(String dimensionName, Query query) {
        Query resolvedQuery = resolveQuery(query);
        WaypointFileManager fileManager = this.filesManager.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            return QueryResult.empty(resolvedQuery);
        }
        return queryManagers(List.of(Map.entry(dimensionName, fileManager)), resolvedQuery);
    }

    public @Unmodifiable List<String> getSearchSuggestions(String dimensionName) {
        WaypointFileManager fileManager = this.filesManager.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            return List.of();
        }
        Set<String> suggestions = new LinkedHashSet<>();
        for (WaypointList waypointList : fileManager.getWaypointLists()) {
            suggestions.add(waypointList.name());
            for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                suggestions.add(waypoint.name());
                suggestions.add(waypoint.initials());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(suggestions));
    }

    private QueryResult queryManagers(List<Map.Entry<String, WaypointFileManager>> managers, Query query) {
        List<DimensionResult> dimensionResults = new ArrayList<>();
        String filter = query.normalizedFilter();
        for (Map.Entry<String, WaypointFileManager> entry : managers) {
            DimensionResult dimensionResult = queryDimension(entry.getKey(), entry.getValue(), filter, query);
            if (!dimensionResult.isEmpty()) {
                dimensionResults.add(dimensionResult);
            }
        }
        return new QueryResult(Collections.unmodifiableList(dimensionResults), query);
    }

    private DimensionResult queryDimension(String dimensionName, WaypointFileManager fileManager, String filter, Query query) {
        List<ListResult> listResults = new ArrayList<>();
        for (WaypointList waypointList : fileManager.getWaypointLists()) {
            ListResult listResult = queryList(dimensionName, waypointList, filter, query);
            if (listResult.include()) {
                listResults.add(listResult);
            }
        }
        return new DimensionResult(dimensionName, Collections.unmodifiableList(listResults));
    }

    private ListResult queryList(String dimensionName, WaypointList waypointList, String filter, Query query) {
        boolean includeAll = filter.isEmpty();
        List<SimpleWaypoint> waypoints = new ArrayList<>();
        for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
            if (includeAll || matchesText(waypoint.name(), filter)) {
                waypoints.add(waypoint);
            }
        }
        WaypointSorting.sort(
                waypoints,
                query.sortMode(),
                query.origin(),
                query.originDimension(),
                dimensionName,
                query.reversed()
        );
        return new ListResult(waypointList, Collections.unmodifiableList(waypoints), includeAll);
    }

    private static boolean matchesText(String text, String filter) {
        if (text == null) {
            return false;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        return normalizedText.contains(filter) || matchesFuzzyText(normalizedText, filter);
    }

    private static boolean matchesFuzzyText(String normalizedText, String filter) {
        if (filter.length() < 3 || !containsLetter(filter)) {
            return false;
        }

        List<String> textTokens = searchTokens(normalizedText);
        if (textTokens.isEmpty()) {
            return false;
        }

        List<String> filterTokens = searchTokens(filter);
        if (filterTokens.isEmpty()) {
            return false;
        }

        for (String filterToken : filterTokens) {
            if (!matchesAnyToken(textTokens, filterToken)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAnyToken(List<String> textTokens, String filterToken) {
        if (filterToken.length() < 3) {
            return false;
        }
        int maxDistance = maxFuzzyDistance(filterToken.length());
        for (String textToken : textTokens) {
            if (Math.abs(textToken.length() - filterToken.length()) <= maxDistance
                    && damerauLevenshteinDistance(textToken, filterToken, maxDistance) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    private static int maxFuzzyDistance(int length) {
        if (length <= 4) {
            return 1;
        }
        if (length <= 8) {
            return 2;
        }
        return 3;
    }

    private static boolean containsLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> searchTokens(String text) {
        List<String> tokens = new ArrayList<>();
        int tokenStart = -1;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                if (tokenStart < 0) {
                    tokenStart = i;
                }
            } else if (tokenStart >= 0) {
                tokens.add(text.substring(tokenStart, i));
                tokenStart = -1;
            }
        }
        if (tokenStart >= 0) {
            tokens.add(text.substring(tokenStart));
        }
        return tokens;
    }

    private static int damerauLevenshteinDistance(String left, String right, int maxDistance) {
        int[] previousPreviousRow = null;
        int[] previousRow = new int[right.length() + 1];
        int[] currentRow = new int[right.length() + 1];

        for (int column = 0; column <= right.length(); column++) {
            previousRow[column] = column;
        }

        for (int row = 1; row <= left.length(); row++) {
            currentRow[0] = row;
            int bestInRow = currentRow[0];
            for (int column = 1; column <= right.length(); column++) {
                int substitutionCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                int distance = Math.min(
                        Math.min(previousRow[column] + 1, currentRow[column - 1] + 1),
                        previousRow[column - 1] + substitutionCost
                );
                if (previousPreviousRow != null
                        && row > 1
                        && column > 1
                        && left.charAt(row - 1) == right.charAt(column - 2)
                        && left.charAt(row - 2) == right.charAt(column - 1)) {
                    distance = Math.min(distance, previousPreviousRow[column - 2] + 1);
                }
                currentRow[column] = distance;
                bestInRow = Math.min(bestInRow, distance);
            }
            if (bestInRow > maxDistance) {
                return maxDistance + 1;
            }
            int[] completedRow = previousRow;
            previousPreviousRow = previousRow;
            previousRow = currentRow;
            currentRow = completedRow;
        }
        return previousRow[right.length()];
    }

    private static @NotNull Query resolveQuery(@Nullable Query query) {
        return query == null ? Query.empty() : query;
    }

    public record Query(
            String filterText,
            WaypointSorting.SortMode sortMode,
            @Nullable WaypointPos origin,
            @Nullable String originDimension,
            boolean reversed
    ) {
        public Query {
            filterText = filterText == null ? "" : filterText;
            sortMode = sortMode == null ? WaypointSorting.SortMode.DEFAULT : sortMode;
            reversed = sortMode != WaypointSorting.SortMode.DEFAULT && reversed;
        }

        public Query(String filterText, WaypointSorting.SortMode sortMode, @Nullable WaypointPos origin, boolean reversed) {
            this(filterText, sortMode, origin, null, reversed);
        }

        public Query(String filterText, WaypointSorting.SortMode sortMode, @Nullable WaypointPos origin) {
            this(filterText, sortMode, origin, null, false);
        }

        public static Query empty() {
            return new Query("", WaypointSorting.SortMode.DEFAULT, null, null, false);
        }

        private String normalizedFilter() {
            return this.filterText.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record QueryResult(@Unmodifiable List<DimensionResult> dimensions, Query query) {
        public static QueryResult empty(Query query) {
            return new QueryResult(List.of(), query);
        }

        public boolean isEmpty() {
            return this.dimensions.isEmpty();
        }

        public int listCount() {
            int count = 0;
            for (DimensionResult dimension : this.dimensions) {
                count += dimension.lists().size();
            }
            return count;
        }

        public int waypointCount() {
            int count = 0;
            for (DimensionResult dimension : this.dimensions) {
                count += dimension.waypointCount();
            }
            return count;
        }
    }

    public record DimensionResult(String dimensionName, @Unmodifiable List<ListResult> lists) {
        public boolean isEmpty() {
            return this.lists.isEmpty();
        }

        public int waypointCount() {
            int count = 0;
            for (ListResult list : this.lists) {
                count += list.waypoints().size();
            }
            return count;
        }
    }

    public record ListResult(WaypointList sourceList, @Unmodifiable List<SimpleWaypoint> waypoints, boolean listMatched) {
        private boolean include() {
            return this.listMatched || !this.waypoints.isEmpty();
        }

        public String listName() {
            return this.sourceList.name();
        }

        public boolean isEmpty() {
            return this.waypoints.isEmpty();
        }
    }
}
