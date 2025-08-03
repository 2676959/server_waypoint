package _959.server_waypoint.server;

import _959.server_waypoint.core.WaypointServerCore;

import java.nio.file.Path;

import static _959.server_waypoint.util.DimensionFileHelper.getDimensionKey;

public class WaypointServerPlugin extends WaypointServerCore {
    public WaypointServerPlugin(Path configDir) {
        super(configDir, WaypointServerPlugin::isFileNameValid);
    }

    public static boolean isFileNameValid(String fileName) {
        return getDimensionKey(fileName) != null;
    }
}
