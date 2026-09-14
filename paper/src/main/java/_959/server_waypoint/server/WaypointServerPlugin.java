package _959.server_waypoint.server;

import _959.server_waypoint.core.WaypointServerCore;
import java.io.IOException;
import java.nio.file.Path;

public class WaypointServerPlugin extends WaypointServerCore {
    public WaypointServerPlugin(Path configDir) {
        super(configDir);
    }

    public void load() throws IOException {
        initConfigAndLanguageResource();
        initializeXaeroWorldId();
        initOrReadWaypointFiles();
    }

    void initializeXaeroWorldId() {
        if (CONFIG.Features().sendXaerosWorldId()) {
            this.setXaeroWorldId(CONFIG.getServerId());
        }
    }
}
