package _959.server_waypoint.listener;

import _959.server_waypoint.ServerWaypointPaperMC;
import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.getWorldId;

public class PlayerRegisterChannelListener implements Listener {
    @EventHandler
    public void onPlayerRegisterChannelEvent(PlayerRegisterChannelEvent event) {
        if (CONFIG.Features().sendXaerosWorldId()) {
            XaerosWorldIdBuffer buffer = new XaerosWorldIdBuffer(getWorldId());
            byte[] bytes = SinglePacketMessageEncoder.encode(buffer);
            event.getPlayer().sendPluginMessage(ServerWaypointPaperMC.getSelf(), buffer.getChannelId().toString(), bytes);
        }
    }
}
