package _959.server_waypoint.network;

import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        Entity executor = source.getExecutor();
        Locale locale = executor instanceof Player player ? player.locale() : Locale.getDefault();
        source.getSender().sendMessage(GlobalTranslator.render(component, locale));
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        player.sendMessage(GlobalTranslator.render(component, player.locale()));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        sendMessage(source, component);
    }

    @Override
    public void broadcastWaypointModification(CommandSourceStack source, WaypointModificationBuffer modification) {
        Server server = source.getSender().getServer();
        server.sendMessage(this.getModificationMessage(source.getSender().name(), modification));
        server.sendPluginMessage(this.plugin, modification.getChannelId().toString(), modification.encode());
    }

    @Override
    public void broadcastPacket(MessageBuffer packet) {
        this.plugin.getServer().getOnlinePlayers().forEach(player -> sendPlayerPacket(player, packet));
    }

    @Override
    public void sendPacket(CommandSourceStack source, MessageBuffer packet) {
        Entity entity = source.getExecutor();
        if (entity instanceof Player player) {
            sendPlayerPacket(player, packet);
        }
    }

    @Override
    public void sendPlayerPacket(Player player, MessageBuffer packet) {
        player.sendPluginMessage(this.plugin, packet.getChannelId().toString(), packet.encode());
    }
}
