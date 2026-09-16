package _959.server_waypoint.core.network;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.PreparedMessage;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveFailure;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveLimits;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

import static _959.server_waypoint.text.WaypointTextHelper.waypointTextNoTp;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;
import static _959.server_waypoint.text.FormattedTextHelper.parse;

public interface PlatformMessageSender<S, P> {
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

    default void broadcastWaypointModification(S source, WaypointModificationMessage modification) {
        Component info = this.getModificationMessage(this.getSenderName(source), modification);
        Iterable<? extends P> recipients = this.getBroadcastPlayers(source);
        for (P player : recipients) {
            this.sendPlayerMessage(player, info);
        }
        this.broadcastChunkedMessage(recipients, modification);
    }

    default void broadcastChunkedMessageFromPlayer(P player, ChunkedMessage message) {
        this.broadcastChunkedMessage(this.getBroadcastPlayersFromPlayer(player), message);
    }

    default Component getModificationMessage(Component senderName, WaypointModificationMessage modification) {
        return switch (modification.type()) {
            case ADD, REMOVE, UPDATE -> {
                Component waypointText;
                if (modification.type() == WaypointModificationType.REMOVE) {
                    waypointText = waypointTextNoTp(modification.waypoint(), modification.dimensionName());
                } else {
                    waypointText = waypointTextWithTp(modification.waypoint(), modification.dimensionName(), modification.listName());
                }
                yield Component.translatable("waypoint.modification.broadcast.player", senderName, modification.type().toTranslatable(), waypointText);
            }
            case ADD_LIST, REMOVE_LIST ->
                Component.translatable("waypoint_list.modification.broadcast.player", senderName, modification.type().toTranslatable(), parse(modification.listDisplayName()));
        };
    }
}
