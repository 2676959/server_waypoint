package _959.server_waypoint.core.network;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.PreparedMessage;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveFailure;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveLimits;
import _959.server_waypoint.text.chat.Chat;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

public interface PlatformMessageSender<S, P> {
    /**
     * A screen ({@link Chat#screen}) ends with one newline, which the game draws as a blank line
     * before the next message. Every other message reaches the player as it is. Platforms apply this
     * when they send; builders never end with a newline.
     */
    static Component forPlayer(Component message) {
        return Chat.isScreen(message) ? Component.empty().append(message).appendNewline() : message;
    }

    /**
     * The copy of a player's view that goes to the commander who ran a command as that player with
     * /execute as: the "Viewed as" line, then the view itself. The player gets the view alone. Pass
     * the view as {@link #forPlayer} makes it, with a screen's trailing newline, for a player
     * commander, and as the builder made it for the console, RCON and command blocks.
     */
    static Component forCommander(Component viewedAsLine, Component view) {
        return Component.empty().append(viewedAsLine).appendNewline().append(view);
    }

    /**
     * Whether this source's feedback is written for a plain-text viewer such as the console, RCON or
     * a command block. The feedback is the view of the player a command runs as, so /execute as a
     * player from the console is that player's view, not plain text; the console reads a copy of it
     * ({@link #forCommander}). Without such a player it is the view of whoever the source is.
     */
    boolean isPlainTextReceiver(S source);

    /**
     * The source whose permissions decide what this source's feedback shows, such as which help
     * lines and buttons appear. It is the source itself unless a commander ran the command as a
     * player with /execute as: then it is that player's own source, so the view carries what the
     * player may do, not what the commander may.
     */
    default S viewingSource(S source) {
        return source;
    }

    void sendMessage(S source, Component component);
    void sendPlayerMessage(P player, Component component);
    void sendError(S source, Component component);
    void sendPacket(S source, SinglePacketMessage message);
    void sendPlayerPacket(P player, SinglePacketMessage message);
    void broadcastPacket(SinglePacketMessage message);
    ChunkedMessageDelivery sendChunkedMessage(S source, ChunkedMessage message);
    Iterable<? extends P> getBroadcastPlayers(S source);
    default Iterable<? extends P> getBroadcastPlayersFromPlayer(P player) {
        return List.of(player);
    }
    Component getSenderName(S source);

    default ChunkedMessageSendResult sendPlayerChunkedMessage(P player, ChunkedMessage message) {
        return this.sendPlayerChunkedMessageTracked(player, message).admissionResult();
    }

    default ChunkedMessageDelivery sendPlayerChunkedMessageTracked(
            P player,
            ChunkedMessage message
    ) {
        if (!this.canSendChunkedMessage(player)) {
            return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        }
        try {
            return this.sendPlayerPreparedChunkedMessageTracked(
                    player,
                    this.prepareChunkedMessage(message)
            );
        } catch (MessageEncodingException exception) {
            WaypointServerCore.LOGGER.warn(
                    "Failed to encode chunked message type {} within the {}-byte logical-message budget for one recipient",
                    message.getClass().getSimpleName(),
                    ChunkedMessageManager.MAX_MESSAGE_BYTES,
                    exception
            );
            this.sendPlayerMessage(
                    player,
                    Component.translatable("waypoint.network.encoding_failed")
            );
            return ChunkedMessageDelivery.rejected(
                    ChunkedMessageSendResult.ENCODING_FAILED
            );
        }
    }

    default ChunkedMessageSendResult sendPlayerPreparedChunkedMessage(
            P player,
            PreparedMessage message
    ) {
        return this.sendPlayerPreparedChunkedMessageTracked(
                player,
                message
        ).admissionResult();
    }

    default ChunkedMessageDelivery sendPlayerPreparedChunkedMessageTracked(
            P player,
            PreparedMessage message
    ) {
        if (!this.canSendChunkedMessage(player)) {
            return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        }
        try {
            return this.chunkedMessageManager().sendTracked(
                    player,
                    message,
                    packets -> this.sendPlayerPacketBatch(player, packets)
            );
        } catch (RuntimeException exception) {
            this.chunkedMessageManager().clear(player);
            WaypointServerCore.LOGGER.warn(
                    "Failed to queue chunked message for one recipient",
                    exception
            );
            return ChunkedMessageDelivery.rejected(
                    ChunkedMessageSendResult.DELIVERY_FAILED
            );
        }
    }

    default PreparedMessage prepareChunkedMessage(ChunkedMessage message) {
        return ChunkedMessageManager.prepare(
                message,
                CONFIG.Features().compressChunkedMessages()
        );
    }

    default void sendPlayerPackets(P player, List<MessageChunkBuffer> packets) {
        for (MessageChunkBuffer packet : packets) {
            this.sendPlayerPacket(player, packet);
        }
    }

    default CompletionStage<ChunkedMessageSendResult> sendPlayerPacketBatch(
            P player,
            List<MessageChunkBuffer> packets
    ) {
        try {
            this.sendPlayerPackets(player, packets);
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.DELIVERY_FAILED
            );
        }
    }

    default CompletionStage<ChunkedMessageSendResult> sendPlayerPacketTracked(
            P player,
            SinglePacketMessage message
    ) {
        try {
            this.sendPlayerPacket(player, message);
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.DELIVERY_FAILED
            );
        }
    }

    default void setChunkedMessageCapable(P player, boolean capable) {
    }

    default boolean canSendChunkedMessage(P player) {
        return true;
    }

    default void broadcastChunkedMessage(ChunkedMessage message) {
        throw new UnsupportedOperationException("This platform cannot broadcast chunked messages");
    }

    default void broadcastChunkedMessage(
            Iterable<? extends P> recipients,
            ChunkedMessage message
    ) {
        PreparedMessage prepared;
        try {
            prepared = this.prepareChunkedMessage(message);
        } catch (MessageEncodingException exception) {
            WaypointServerCore.LOGGER.warn(
                    "Failed to encode chunked broadcast type {} within the {}-byte logical-message budget",
                    message.getClass().getSimpleName(),
                    ChunkedMessageManager.MAX_MESSAGE_BYTES,
                    exception
            );
            return;
        }
        for (P recipient : recipients) {
            this.sendPlayerPreparedChunkedMessage(recipient, prepared);
        }
    }

    default boolean receiveChunkedMessage(
            P player,
            MessageChunkBuffer packet,
            Consumer<ChunkedMessage> handler
    ) {
        return this.chunkedMessageManager().receiveAndApply(player, packet, handler);
    }

    default boolean receiveChunkedMessage(
            P player,
            MessageChunkBuffer packet,
            ReceiveLimits limits,
            Consumer<ChunkedMessage> handler
    ) {
        return this.chunkedMessageManager().receiveAndApply(player, packet, limits, handler);
    }

    default List<ReceiveFailure<P>> tickChunkedMessages() {
        List<ReceiveFailure<P>> failures = this.chunkedMessageManager().tick();
        failures.forEach(this::logChunkedMessageFailure);
        return failures;
    }

    default boolean hasPendingChunkedMessages(P player) {
        return this.chunkedMessageManager().hasPending(player);
    }

    default void disconnectChunkedMessages(P player) {
        this.chunkedMessageManager().clear(player);
    }

    private void logChunkedMessageFailure(ReceiveFailure<P> failure) {
        WaypointServerCore.LOGGER.warn(
                "Discarded incomplete chunked message type {} from peer {}: {} (transfer {})",
                failure.messageTypeId(),
                failure.peer(),
                failure.reason(),
                failure.transferId().map(Object::toString).orElse("unknown")
        );
    }

    private ChunkedMessageManager<P> chunkedMessageManager() {
        return ChunkedMessageManagerRegistry.get(this);
    }

    default void broadcastChunkedMessageFromPlayer(P player, ChunkedMessage message) {
        this.broadcastChunkedMessage(this.getBroadcastPlayersFromPlayer(player), message);
    }
}
