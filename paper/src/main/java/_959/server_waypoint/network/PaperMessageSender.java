package _959.server_waypoint.network;

import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static _959.server_waypoint.text.WaypointTextHelper.defaultWaypointText;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        source.getSender().sendMessage(component);
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        player.sendMessage(component);
    }

    @Override
    public void sendFeedback(CommandSourceStack source, Component component, boolean broadcastToOps) {
        source.getSender().sendMessage(component);
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        source.getSender().sendMessage(component);
    }

    @Override
    public void broadcastWaypointModification(CommandSourceStack source, WaypointModificationBuffer modification) {
        Component waypointText = defaultWaypointText(modification.waypoint(), modification.dimensionName(), modification.listName());
        Server server = source.getSender().getServer();
        server.sendMessage(Component.translatable("waypoint.modification.broadcast.player", source.getSender().name(), modification.type().toTranslatable(), waypointText));
        server.sendPluginMessage(this.plugin, modification.getChannelId().toString(), modification.encode());
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
