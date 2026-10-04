package _959.server_waypoint.network;

import _959.server_waypoint.core.logging.PlayerActionLog;
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
import _959.server_waypoint.server.command.CommandChatIcons;
import _959.server_waypoint.text.chat.Chat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public class PaperMessageSender implements PlatformMessageSender<CommandSourceStack, Player> {
    private final JavaPlugin plugin;
    private final PaperScheduler scheduler;
    private final Set<UUID> chunkedMessageCapablePlayers = ConcurrentHashMap.newKeySet();
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
                ignored -> this.tickChunkedMessageMaintenance(),
                50L,
                50L,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * One manager-wide maintenance tick per period: expiry, then a round-robin
     * outbound grant round bounded by one global frame and byte budget. Batch
     * emission still hops to each player's owning region through the
     * {@link OwnerThreadDispatcher}.
     */
    private void tickChunkedMessageMaintenance() {
        runChunkedMessageMaintenance(
                () -> this.tickChunkedMessages(),
                message -> this.plugin.getLogger().warning(message)
        );
    }

    static void runChunkedMessageMaintenance(
            Runnable managerTick,
            Consumer<String> warningLogger
    ) {
        try {
            managerTick.run();
        } catch (RuntimeException exception) {
            warningLogger.accept(
                    "Chunked-message maintenance tick failed: " + exception
            );
        }
    }

    /** One message and who gets it. */
    record Delivery(CommandSender recipient, Component message) {
    }

    /**
     * The player whose view the feedback is: the player a command runs as, such as /execute as a
     * player, else the sender when that is a player. Null for the console, RCON and command blocks,
     * which read plain text.
     */
    static @Nullable Player viewingPlayer(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player executor) {
            return executor;
        }
        return source.getSender() instanceof Player sender ? sender : null;
    }

    @Override
    public boolean isPlainTextReceiver(CommandSourceStack source) {
        return viewingPlayer(source) == null;
    }

    /**
     * The stack of the player the feedback is viewed as when a commander ran it for them, so the
     * view shows what that player may do; otherwise the stack itself. Paper's stack is the vanilla
     * one, so the player's own stack is an API stack too.
     */
    @Override
    public CommandSourceStack viewingSource(CommandSourceStack source) {
        Player viewer = viewingPlayer(source);
        if (viewer == null || viewer.equals(source.getSender()) || !(viewer instanceof CraftPlayer craftViewer)) {
            return source;
        }
        return craftViewer.getHandle().createCommandSourceStack();
    }

    /**
     * The feedback goes to the player whose view it is, and Paper renders it in their language.
     * When a commander ran it for that player with /execute as, the commander also gets the view,
     * under a "Viewed as" line that Paper renders in the commander's language. The view itself is
     * rendered here, in the player's language, since Paper would render it in the commander's. A
     * commander who is not a player reads it without chat objects.
     */
    static List<Delivery> deliveries(CommandSourceStack source, Component component) {
        CommandSender commander = source.getSender();
        Player viewer = viewingPlayer(source);
        if (viewer == null) {
            return List.of(new Delivery(commander, component));
        }
        Component view = PlatformMessageSender.forPlayer(component);
        if (commander.equals(viewer)) {
            return List.of(new Delivery(viewer, view));
        }
        Component viewedByCommander = commander instanceof Player
                ? GlobalTranslator.render(view, viewer.locale())
                : CommandChatIcons.INSTANCE.withoutIcons(GlobalTranslator.render(component, viewer.locale()));
        return List.of(
                new Delivery(viewer, view),
                new Delivery(commander, PlatformMessageSender.forCommander(Chat.viewedAs(viewer.getName()), viewedByCommander))
        );
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        for (Delivery delivery : deliveries(source, component)) {
            CommandSender recipient = delivery.recipient();
            this.scheduler.execute(recipient, () -> recipient.sendMessage(delivery.message()));
        }
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        Component message = PlatformMessageSender.forPlayer(component);
        this.scheduler.execute(player, () -> player.sendMessage(message));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        this.sendMessage(source, component);
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
    public PlayerActionLog.Actor playerActor(Player player) {
        return new PlayerActionLog.Actor(player.getUniqueId(), player.getName());
    }

    @Override
    public PlayerActionLog.Actor commandSenderActor(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        return sender instanceof Player player ? this.playerActor(player)
                : new PlayerActionLog.Actor(null, sender.getName());
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
        } else {
            this.chunkedMessageSessions.remove(playerId);
            this.chunkedMessageCapablePlayers.remove(playerId);
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
