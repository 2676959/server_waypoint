package _959.server_waypoint;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.network.PaperChatMessageHandler;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

public class ServerWaypointListener implements Listener {
    private final PaperChatMessageHandler chatMessageHandler;

    public ServerWaypointListener(PaperChatMessageHandler chatMessageHandler) {
        this.chatMessageHandler = chatMessageHandler;
    }

    @EventHandler
    public void onPlayerRegisterChannelEvent(PlayerRegisterChannelEvent event) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(0);
        out.writeInt(WaypointServerCore.getWorldId());
        event.getPlayer().sendPluginMessage(ServerWaypointPaperMC.getSelf(), ServerWaypointPaperMC.minimapChannel, out.toByteArray());
        event.getPlayer().sendPluginMessage(ServerWaypointPaperMC.getSelf(), ServerWaypointPaperMC.worldmapChannel, out.toByteArray());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        this.chatMessageHandler.onChatMessage(event.getPlayer(), event.signedMessage().message());
    }
}
