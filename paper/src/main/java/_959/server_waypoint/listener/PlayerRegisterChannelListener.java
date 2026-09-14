package _959.server_waypoint.listener;

import _959.server_waypoint.network.XaeroCompatibilityPayloads;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.getWorldId;
import static _959.server_waypoint.core.network.MessageChannelID.XAEROS_WORLD_ID_CHANNEL;

public class PlayerRegisterChannelListener implements Listener {
    private final JavaPlugin plugin;

    public PlayerRegisterChannelListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRegisterChannelEvent(PlayerRegisterChannelEvent event) {
        if (!CONFIG.Features().sendXaerosWorldId()) {
            return;
        }
        if (XaeroCompatibilityPayloads.XAEROLIB_CHANNEL.equals(event.getChannel())) {
            sendXaeroLibHandshake(event.getPlayer());
        } else if (XaeroCompatibilityPayloads.XAERO_WORLD_MAP_CHANNEL.equals(event.getChannel())) {
            sendWorldMapState(event.getPlayer());
        } else if (XAEROS_WORLD_ID_CHANNEL.ID.equals(event.getChannel())) {
            sendMinimapState(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Paper sends level info again after this event. Keep this synchronous so XaeroLib
        // receives its dimension handshake before checking the following border packet.
        sendXaeroState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        sendXaeroState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sendXaeroState(event.getPlayer());
    }

    private void sendXaeroState(Player player) {
        if (!CONFIG.Features().sendXaerosWorldId() || !player.isOnline()) {
            return;
        }
        Set<String> channels = player.getListeningPluginChannels();
        if (channels.contains(XaeroCompatibilityPayloads.XAEROLIB_CHANNEL)) {
            sendXaeroLibHandshake(player);
        }
        if (channels.contains(XaeroCompatibilityPayloads.XAERO_WORLD_MAP_CHANNEL)) {
            sendWorldMapState(player);
        }
        if (channels.contains(XAEROS_WORLD_ID_CHANNEL.ID)) {
            sendMinimapState(player);
        }
    }

    private void sendXaeroLibHandshake(Player player) {
        player.sendPluginMessage(
                this.plugin,
                XaeroCompatibilityPayloads.XAEROLIB_CHANNEL,
                XaeroCompatibilityPayloads.serverHandshake()
        );
        player.sendPluginMessage(
                this.plugin,
                XaeroCompatibilityPayloads.XAEROLIB_CHANNEL,
                XaeroCompatibilityPayloads.dimensionHandshake()
        );
    }

    private void sendMinimapState(Player player) {
        player.sendPluginMessage(
                this.plugin,
                XAEROS_WORLD_ID_CHANNEL.ID,
                XaeroCompatibilityPayloads.worldId(getWorldId())
        );
    }

    private void sendWorldMapState(Player player) {
        player.sendPluginMessage(
                this.plugin,
                XaeroCompatibilityPayloads.XAERO_WORLD_MAP_CHANNEL,
                XaeroCompatibilityPayloads.worldId(getWorldId())
        );
    }
}
