package _959.server_waypoint.server;

import _959.server_waypoint.core.WaypointServerCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

import static _959.server_waypoint.util.DimensionFileHelper.getNamespacedKey;

public class WaypointServerPlugin extends WaypointServerCore {
    public static final Logger log = LoggerFactory.getLogger("server_waypoint_plugin");

    public WaypointServerPlugin(Path configDir, Path saveDir) {
        super(configDir);
        if (CONFIG.Features().sendXaerosWorldId()) {
            this.initXearoWorldId(saveDir);
        }
    }

    public void load() throws IOException {
        initConfigAndLanguageResource();
        initOrReadWaypointFiles();
    }

    @Override
    public boolean isDimensionKeyValid(String dimString) {
        return false;
    }

    public static boolean isFileNameValid(String fileName) {
        return getNamespacedKey(fileName) != null;
    }
}
