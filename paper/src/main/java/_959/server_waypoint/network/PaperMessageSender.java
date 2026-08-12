package _959.server_waypoint.network;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;
    private final PaperScheduler scheduler;

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new PaperScheduler(plugin);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> this.tickChunkedMessages(),
                1L,
                1L,
                TimeUnit.SECONDS
        );
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
    public void broadcastPacket(SinglePacketMessage message) {
        this.plugin.getServer().getOnlinePlayers().forEach(player -> sendPlayerPacket(player, message));
    }

    @Override
    public void broadcastChunkedMessage(ChunkedMessage message) {
        this.plugin.getServer().getOnlinePlayers()
                .forEach(player -> this.sendPlayerChunkedMessage(player, message));
    }

    @Override
    public void sendPacket(CommandSourceStack source, SinglePacketMessage message) {
        Entity entity = source.getExecutor();
        if (entity instanceof Player player) {
            sendPlayerPacket(player, message);
        }
    }

    @Override
    public void sendChunkedMessage(CommandSourceStack source, ChunkedMessage message) {
        Entity entity = source.getExecutor();
        if (entity instanceof Player player) {
            this.sendPlayerChunkedMessage(player, message);
        }
    }

    @Override
    public void sendPlayerPacket(Player player, SinglePacketMessage message) {
        try {
            byte[] payload = SinglePacketMessageEncoder.encode(message);
            this.scheduler.execute(
                    player,
                    () -> player.sendPluginMessage(
                            this.plugin,
                            message.getChannelId().toString(),
                            payload
                    )
            );
        } catch (MessageEncodingException exception) {
            this.plugin.getLogger().warning(
                    "Failed to encode single-packet message type "
                            + message.getClass().getSimpleName()
                            + " within the "
                            + SinglePacketMessageEncoder.MAX_ENCODED_BYTES
                            + "-byte packet budget"
            );
            this.sendPlayerMessage(
                    player,
                    Component.translatable("waypoint.network.encoding_failed")
            );
        }
    }
}
