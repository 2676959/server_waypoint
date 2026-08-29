package _959.server_waypoint.network;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.PreparedMessage;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;
    private final PaperScheduler scheduler;
    private final Set<UUID> chunkedMessageCapablePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Player> chunkedMessagePlayers = new ConcurrentHashMap<>();
    private final Set<UUID> scheduledChunkedMessageDispatches =
            ConcurrentHashMap.newKeySet();

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new PaperScheduler(plugin);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> this.tickChunkedMessagePlayers(),
                50L,
                50L,
                TimeUnit.MILLISECONDS
        );
    }

    private void tickChunkedMessagePlayers() {
        for (Map.Entry<UUID, Player> entry : this.chunkedMessagePlayers.entrySet()) {
            UUID playerId = entry.getKey();
            Player player = entry.getValue();
            if (!this.hasPendingChunkedMessages(player)
                    || !this.scheduledChunkedMessageDispatches.add(playerId)) {
                continue;
            }
            boolean scheduled;
            try {
                scheduled = this.scheduler.execute(
                        player,
                        () -> {
                            try {
                                this.tickChunkedMessages(player);
                            } finally {
                                this.scheduledChunkedMessageDispatches.remove(playerId);
                            }
                        }
                );
            } catch (RuntimeException exception) {
                scheduled = false;
            }
            if (!scheduled) {
                this.scheduledChunkedMessageDispatches.remove(playerId);
                this.disconnectChunkedMessages(player);
            }
        }
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
        this.broadcastChunkedMessage(this.plugin.getServer().getOnlinePlayers(), message);
    }

    @Override
    public ChunkedMessageSendResult sendPlayerChunkedMessage(Player player, ChunkedMessage message) {
        UUID playerId = player.getUniqueId();
        if (!this.chunkedMessageCapablePlayers.contains(playerId)) {
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        return PlatformMessageSender.super.sendPlayerChunkedMessage(player, message);
    }

    @Override
    public ChunkedMessageSendResult sendPlayerPreparedChunkedMessage(
            Player player,
            PreparedMessage message
    ) {
        UUID playerId = player.getUniqueId();
        if (!this.chunkedMessageCapablePlayers.contains(playerId)) {
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        try {
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                return this.sendOwnedChunkedMessage(player, message, playerId);
            }
            boolean scheduled = this.scheduler.execute(
                    player,
                    () -> this.sendOwnedChunkedMessage(player, message, playerId)
            );
            if (!scheduled) {
                this.disconnectChunkedMessages(player);
                return ChunkedMessageSendResult.DELIVERY_FAILED;
            }
            return ChunkedMessageSendResult.QUEUED;
        } catch (RuntimeException exception) {
            this.disconnectChunkedMessages(player);
            this.plugin.getLogger().warning(
                    "Failed to schedule chunked message for " + playerId
            );
            return ChunkedMessageSendResult.DELIVERY_FAILED;
        }
    }

    private ChunkedMessageSendResult sendOwnedChunkedMessage(
            Player player,
            PreparedMessage message,
            UUID playerId
    ) {
        if (!this.chunkedMessageCapablePlayers.contains(playerId)
                || !player.getListeningPluginChannels().contains(
                        MessageChannelID.MESSAGE_CHUNK_CHANNEL.ID
                )) {
            this.setChunkedMessageCapable(player, false);
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        ChunkedMessageSendResult result =
                PlatformMessageSender.super.sendPlayerPreparedChunkedMessage(player, message);
        if (result == ChunkedMessageSendResult.DELIVERY_FAILED) {
            this.setChunkedMessageCapable(player, false);
        }
        return result;
    }

    @Override
    public void setChunkedMessageCapable(Player player, boolean capable) {
        UUID playerId = player.getUniqueId();
        if (capable) {
            this.chunkedMessageCapablePlayers.add(playerId);
            this.chunkedMessagePlayers.put(playerId, player);
        } else {
            this.chunkedMessageCapablePlayers.remove(playerId);
            this.chunkedMessagePlayers.remove(playerId);
            this.scheduledChunkedMessageDispatches.remove(playerId);
            PlatformMessageSender.super.disconnectChunkedMessages(player);
        }
    }

    @Override
    public boolean canSendChunkedMessage(Player player) {
        return this.chunkedMessageCapablePlayers.contains(player.getUniqueId());
    }

    @Override
    public void disconnectChunkedMessages(Player player) {
        this.setChunkedMessageCapable(player, false);
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

    @Override
    public void sendPlayerPackets(Player player, List<MessageChunkBuffer> packets) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException(
                    "Chunked-message batch delivery requires the player's owning region"
            );
        }
        this.sendOwnedPacketBatch(player, packets);
    }

    private void sendOwnedPacketBatch(Player player, List<MessageChunkBuffer> packets) {
        UUID playerId = player.getUniqueId();
        if (!this.chunkedMessageCapablePlayers.contains(playerId)
                || !player.getListeningPluginChannels().contains(
                        MessageChannelID.MESSAGE_CHUNK_CHANNEL.ID
                )) {
            this.setChunkedMessageCapable(player, false);
            return;
        }
        for (MessageChunkBuffer packet : packets) {
            byte[] payload = SinglePacketMessageEncoder.encode(packet);
            player.sendPluginMessage(
                    this.plugin,
                    packet.getChannelId().toString(),
                    payload
            );
        }
    }
}
