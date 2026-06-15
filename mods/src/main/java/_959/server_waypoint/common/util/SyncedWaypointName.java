package _959.server_waypoint.common.util;

public final class SyncedWaypointName {
    private static final String SERVER_WAYPOINT_PREFIX = "sw";
    private static final String NAME_SEPARATOR = "\u241F";
    private static final String SERVER_WAYPOINT_NAME_PREFIX = SERVER_WAYPOINT_PREFIX + NAME_SEPARATOR;

    private SyncedWaypointName() {
    }

    public static String format(String listName, String waypointName) {
        if (listName == null || waypointName == null || listName.contains(NAME_SEPARATOR) || waypointName.contains(NAME_SEPARATOR)) {
            return null;
        }
        return SERVER_WAYPOINT_NAME_PREFIX + listName + NAME_SEPARATOR + waypointName;
    }

    public static String formatSyncedName(String name) {
        if (name == null || name.contains(NAME_SEPARATOR)) {
            return null;
        }
        return SERVER_WAYPOINT_NAME_PREFIX + name;
    }

    public static ParsedName parse(String waypointName) {
        if (waypointName == null || !waypointName.startsWith(SERVER_WAYPOINT_NAME_PREFIX)) {
            return null;
        }
        int listNameStart = SERVER_WAYPOINT_NAME_PREFIX.length();
        int separatorIndex = waypointName.indexOf(NAME_SEPARATOR, listNameStart);
        if (separatorIndex < 0 || waypointName.indexOf(NAME_SEPARATOR, separatorIndex + NAME_SEPARATOR.length()) >= 0) {
            return null;
        }
        return new ParsedName(
                waypointName.substring(listNameStart, separatorIndex),
                waypointName.substring(separatorIndex + NAME_SEPARATOR.length())
        );
    }

    public static String parseSyncedName(String name) {
        if (name == null || !name.startsWith(SERVER_WAYPOINT_NAME_PREFIX)) {
            return null;
        }
        String parsedName = name.substring(SERVER_WAYPOINT_NAME_PREFIX.length());
        if (parsedName.contains(NAME_SEPARATOR)) {
            return null;
        }
        return parsedName;
    }

    public static String toDisplayWaypointName(String name) {
        String parsedName = parseSyncedName(name);
        return parsedName == null ? name : parsedName;
    }

    public static String toDisplayXaerosWorldMapName(String name) {
        return toDisplayWaypointName(name);
    }

    public static String toDisplayVoxelMapWaypointName(String name) {
        ParsedName parsedName = parse(name);
        return parsedName == null ? name : parsedName.waypointName();
    }

    public record ParsedName(String listName, String waypointName) {
    }
}
