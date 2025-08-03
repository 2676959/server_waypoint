package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.server.WaypointServerPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;

public class ServerWaypointPaperMC extends JavaPlugin implements IPlatformConfigPath {
    @Override
    public void onEnable() {
        // Plugin startup logic
        WaypointServerPlugin waypointServerPlugin = new WaypointServerPlugin(this.getAssignedConfigDirectory());
        try {
            waypointServerPlugin.initServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }



    @Override
    public Path getAssignedConfigDirectory() {
        return getDataFolder().toPath();
    }
}
