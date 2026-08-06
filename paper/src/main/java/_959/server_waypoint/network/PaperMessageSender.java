package _959.server_waypoint.network;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;
    private final PaperScheduler scheduler;

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new PaperScheduler(plugin);
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        CommandSender sender = source.getSender();
        this.scheduler.execute(sender, () -> sender.sendMessage(component));
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        this.scheduler.execute(player, () -> player.sendMessage(component));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        CommandSender sender = source.getSender();
        this.scheduler.execute(sender, () -> sender.sendMessage(component));
    }

    @Override
    public Collection<? extends Player> getBroadcastPlayers(CommandSourceStack source) {
        return source.getSender().getServer().getOnlinePlayers();
    }

    @Override
    public Collection<? extends Player> getBroadcastPlayersFromPlayer(Player player) {
        return player.getServer().getOnlinePlayers();
    }

    @Override
    public Component getSenderName(CommandSourceStack source) {
        return source.getSender().name();
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
        byte[] payload = packet.encode();
        this.scheduler.execute(
                player,
                () -> player.sendPluginMessage(
                        this.plugin,
                        packet.getChannelId().toString(),
                        payload
                )
        );
    }
}
