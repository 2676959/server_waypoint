package _959.server_waypoint.listener;

import _959.server_waypoint.network.PaperChatMessageHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatMessageListenerPaperMC implements Listener {
    private final PaperChatMessageHandler chatMessageHandler;

    public ChatMessageListenerPaperMC(PaperChatMessageHandler chatMessageHandler) {
        this.chatMessageHandler = chatMessageHandler;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {

        this.chatMessageHandler.onChatMessage(event.getPlayer(), event.signedMessage().message());
    }
}
