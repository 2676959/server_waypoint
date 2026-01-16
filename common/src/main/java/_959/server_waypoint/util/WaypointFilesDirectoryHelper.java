package _959.server_waypoint.util;

import java.nio.file.Path;

public class WaypointFilesDirectoryHelper {
    public static Path asDedicatedServer(Path configPath) {
        return configPath.resolve("waypoints");
    }

    public static Path asIntegratedServer(Path savePath) {
        return savePath.resolve("server_waypoint").resolve("waypoints");
    }

    /**
     * For waypoints that are sharing by a server
     * */
    public static Path asClientFromRemoteServer(Path gameRoot, String serverIp, int serverId) {
        return gameRoot.resolve("server_waypoint").resolve(toSafeFolderName(serverIp)).resolve("server").resolve(String.valueOf(serverId));
    }

    /**
     * For waypoints that are created and saved by user for itself
     * */
    public static Path asClientFromLocal(Path gameRoot, String serverIp, int serverId) {
        return gameRoot.resolve("server_waypoint").resolve(toSafeFolderName(serverIp)).resolve("local").resolve(String.valueOf(serverId));
    }

    public static Path asClientFromLocalByUnidentifiedServer(Path gameRoot, String serverIp) {
        return gameRoot.resolve("server_waypoint").resolve(toSafeFolderName(serverIp)).resolve("local");
    }

    public static String toSafeFolderName(String name) {
        // 1. Replace colons (IPv6) with underscores
        String safe = name.replace(':', '_');

        // 2. Remove trailing dot if present (FQDN)
        if (safe.endsWith(".")) {
            safe = safe.substring(0, safe.length() - 1);
        }

        // 3. Prepend underscore to prevent Windows Reserved Word conflicts
        return "_" + safe;
    }
}
