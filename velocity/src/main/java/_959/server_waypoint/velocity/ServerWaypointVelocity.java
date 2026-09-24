package _959.server_waypoint.velocity;

import _959.server_waypoint.ModInfo;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

@Plugin(id = "server_waypoint", name = "Server Waypoint", version = ModInfo.MOD_VERSION,
        authors = {"2676959"}, description = "Server Waypoint proxy coordinator")
public final class ServerWaypointVelocity {
    static final MinecraftChannelIdentifier RESERVED = MinecraftChannelIdentifier.create("server_waypoint", "handoff");
    private final Logger logger;
    private final ProxyServer proxy;
    private final VelocityRuntime runtime;
    @Inject public ServerWaypointVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path directory) {
        this.proxy = proxy; this.logger = logger; runtime = new VelocityRuntime(proxy, directory);
    }
    @Subscribe public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(RESERVED);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("serverwaypoint").plugin(this).build(),
                new CrossServerStatusCommand(runtime));
        runtime.start().thenAccept(result -> {
            String details = runtime.startupFailureDetails();
            if (details == null) logger.info("Server Waypoint coordinator startup: {}", result);
            else logger.warn("Server Waypoint coordinator startup: {}. {}", result, details);
        });
    }
    @Subscribe public void onDisconnect(DisconnectEvent event) { runtime.disconnected(event.getPlayer().getUniqueId()); }
    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (event.getIdentifier().equals(RESERVED)) {
            // Neither clients nor plugin-message backends may authorize control-plane requests.
            // Consume before inspecting any source or payload; never forward this reserved channel.
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }
    @Subscribe public com.velocitypowered.api.event.EventTask onShutdown(ProxyShutdownEvent event) {
        proxy.getChannelRegistrar().unregister(RESERVED);
        return com.velocitypowered.api.event.EventTask.withContinuation(continuation -> runtime.stop().whenComplete((result, failure) -> {
            logger.info("Server Waypoint coordinator stopped: {}", result); continuation.resume();
        }));
    }
}
