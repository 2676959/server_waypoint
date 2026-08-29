package _959.server_waypoint.network;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;
    private final PaperScheduler scheduler;
    private final Set<UUID> chunkedMessageCapablePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Player> chunkedMessagePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Object> chunkedMessageSessions = new ConcurrentHashMap<>();
    private final OwnerThreadDispatcher<Player> ownerThreadDispatcher;

    public PaperMessageSender(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new PaperScheduler(plugin);
        this.ownerThreadDispatcher = new OwnerThreadDispatcher<>(
                Bukkit::isOwnedByCurrentRegion,
                this.scheduler::execute
        );
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
            Player player = entry.getValue();
            try {
                if (this.hasPendingChunkedMessages(player)) {
                    this.tickChunkedMessages(player);
                }
            } catch (RuntimeException exception) {
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
    public void setChunkedMessageCapable(Player player, boolean capable) {
        UUID playerId = player.getUniqueId();
        if (capable) {
            PlatformMessageSender.super.disconnectChunkedMessages(player);
            this.chunkedMessageSessions.put(playerId, new Object());
            this.chunkedMessageCapablePlayers.add(playerId);
            this.chunkedMessagePlayers.put(playerId, player);
        } else {
            this.chunkedMessageSessions.remove(playerId);
            this.chunkedMessageCapablePlayers.remove(playerId);
            this.chunkedMessagePlayers.remove(playerId);
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
    public ChunkedMessageDelivery sendChunkedMessage(
            CommandSourceStack source,
            ChunkedMessage message
    ) {
        Entity entity = source.getExecutor();
        if (entity instanceof Player player) {
            return this.sendPlayerChunkedMessageTracked(player, message);
        }
        return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
    }

    @Override
    public void sendPlayerPacket(Player player, SinglePacketMessage message) {
        this.sendPlayerPacketTracked(player, message).thenAccept(result -> {
            if (!result.delivered()) {
                this.plugin.getLogger().warning(
                        "Failed to deliver single-packet message type "
                                + message.getClass().getSimpleName()
                                + " to "
                                + player.getUniqueId()
                                + ": "
                                + result
                );
            }
        });
    }

    @Override
    public CompletionStage<ChunkedMessageSendResult> sendPlayerPacketTracked(
            Player player,
            SinglePacketMessage message
    ) {
        byte[] payload;
        try {
            payload = SinglePacketMessageEncoder.encode(message);
        } catch (MessageEncodingException exception) {
            this.plugin.getLogger().warning(
                    "Failed to encode single-packet message type "
                            + message.getClass().getSimpleName()
                            + " within the "
                            + SinglePacketMessageEncoder.MAX_ENCODED_BYTES
                            + "-byte packet budget"
            );
            return CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.ENCODING_FAILED
            );
        }
        return this.ownerThreadDispatcher.dispatch(
                player,
                () -> {
                    player.sendPluginMessage(
                            this.plugin,
                            message.getChannelId().toString(),
                            payload
                    );
                    return ChunkedMessageSendResult.DELIVERED;
                }
        );
    }

    @Override
    public void sendPlayerPackets(Player player, List<MessageChunkBuffer> packets) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException(
                    "Chunked-message batch delivery requires the player's owning region"
            );
        }
        UUID playerId = player.getUniqueId();
        ChunkedMessageSendResult result = this.sendOwnedPacketBatch(
                player,
                packets,
                this.chunkedMessageSessions.get(playerId)
        );
        if (!result.delivered()) {
            throw new IllegalStateException("Chunked-message batch delivery failed: " + result);
        }
    }

    @Override
    public CompletionStage<ChunkedMessageSendResult> sendPlayerPacketBatch(
            Player player,
            List<MessageChunkBuffer> packets
    ) {
        UUID playerId = player.getUniqueId();
        Object session = this.chunkedMessageSessions.get(playerId);
        return this.ownerThreadDispatcher.dispatch(
                player,
                () -> this.sendOwnedPacketBatch(player, packets, session)
        );
    }

    private ChunkedMessageSendResult sendOwnedPacketBatch(
            Player player,
            List<MessageChunkBuffer> packets,
            Object session
    ) {
        UUID playerId = player.getUniqueId();
        if (this.chunkedMessageSessions.get(playerId) != session) {
            return ChunkedMessageSendResult.DELIVERY_FAILED;
        }
        if (!this.chunkedMessageCapablePlayers.contains(playerId)
                || !player.getListeningPluginChannels().contains(
                        MessageChannelID.MESSAGE_CHUNK_CHANNEL.ID
                )) {
            this.setChunkedMessageCapable(player, false);
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        for (MessageChunkBuffer packet : packets) {
            byte[] payload = SinglePacketMessageEncoder.encode(packet);
            player.sendPluginMessage(
                    this.plugin,
                    packet.getChannelId().toString(),
                    payload
            );
        }
        return ChunkedMessageSendResult.DELIVERED;
    }
}
