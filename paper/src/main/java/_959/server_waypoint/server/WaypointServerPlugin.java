package _959.server_waypoint.server;

import _959.server_waypoint.core.WaypointServerCore;
import java.io.IOException;
import java.nio.file.Path;

public class WaypointServerPlugin extends WaypointServerCore {
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
}
