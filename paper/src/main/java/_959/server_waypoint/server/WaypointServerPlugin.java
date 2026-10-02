package _959.server_waypoint.server;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.util.NamespacedId;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import java.io.IOException;
import java.nio.file.Path;

public class WaypointServerPlugin extends WaypointServerCore {
    @Override
    protected boolean isRegisteredIconItem(NamespacedId icon) {
        Material material = Registry.MATERIAL.get(new NamespacedKey(icon.namespace(), icon.path()));
        return material != null && !material.isLegacy() && material.isItem() && !material.isAir();
    }

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
