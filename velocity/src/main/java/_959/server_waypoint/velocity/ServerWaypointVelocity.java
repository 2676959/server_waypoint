package _959.server_waypoint.velocity;

import _959.server_waypoint.ModInfo;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

/** Lifecycle skeleton only. No listener, backend client, command, channel, or transfer is registered. */
@Plugin(id = "server_waypoint", name = "Server Waypoint", version = ModInfo.MOD_VERSION,
        authors = {"2676959"}, description = "Server Waypoint proxy module")
public final class ServerWaypointVelocity {
    private final Logger logger;

    @Inject
    public ServerWaypointVelocity(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        logger.info("Server Waypoint proxy module initialized; cross-server transport is disabled.");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        // This skeleton owns no sockets or workers; later services must finish shutdown here.
        logger.info("Server Waypoint proxy module stopped.");
    }
}
