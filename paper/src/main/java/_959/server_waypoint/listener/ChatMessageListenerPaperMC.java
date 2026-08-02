package _959.server_waypoint.listener;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.network.PaperChatMessageHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatMessageListenerPaperMC implements Listener {
    private final PaperChatMessageHandler chatMessageHandler;
    private final PaperScheduler scheduler;

    public ChatMessageListenerPaperMC(
            JavaPlugin plugin,
            PaperChatMessageHandler chatMessageHandler
    ) {
        this.chatMessageHandler = chatMessageHandler;
        this.scheduler = new PaperScheduler(plugin);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = event.signedMessage().message();
        this.scheduler.execute(
                player,
                () -> this.chatMessageHandler.onChatMessage(player, message)
        );
    }
}
