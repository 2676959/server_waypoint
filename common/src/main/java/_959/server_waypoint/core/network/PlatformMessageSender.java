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
    void sendChunkedMessage(S source, ChunkedMessage message);
    Iterable<? extends P> getBroadcastPlayers(S source);
    default Iterable<? extends P> getBroadcastPlayersFromPlayer(P player) {
        return List.of(player);
    }
    Component getSenderName(S source);

    default ChunkedMessageSendResult sendPlayerChunkedMessage(P player, ChunkedMessage message) {
        if (!this.canSendChunkedMessage(player)) {
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        try {
            return this.sendPlayerPreparedChunkedMessage(
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
            return ChunkedMessageSendResult.ENCODING_FAILED;
        }
    }

    default ChunkedMessageSendResult sendPlayerPreparedChunkedMessage(
            P player,
            PreparedMessage message
    ) {
        if (!this.canSendChunkedMessage(player)) {
            return ChunkedMessageSendResult.UNSUPPORTED;
        }
        try {
            return this.chunkedMessageManager().send(
                    player,
                    message,
                    packets -> this.sendPlayerPackets(player, packets)
            );
        } catch (RuntimeException exception) {
            this.chunkedMessageManager().clear(player);
            WaypointServerCore.LOGGER.warn(
                    "Failed to queue chunked message for one recipient",
                    exception
            );
            return ChunkedMessageSendResult.DELIVERY_FAILED;
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

    default List<ChunkedMessage> receiveChunkedMessage(P player, MessageChunkBuffer packet) {
        return this.chunkedMessageManager().receive(player, packet);
    }

    default List<ChunkedMessage> receiveChunkedMessage(
            P player,
            MessageChunkBuffer packet,
            ReceiveLimits limits
    ) {
        return this.chunkedMessageManager().receive(player, packet, limits);
    }

    default List<ReceiveFailure<P>> tickChunkedMessages() {
        List<ReceiveFailure<P>> failures = this.chunkedMessageManager().tick();
        failures.forEach(this::logChunkedMessageFailure);
        return failures;
    }

    default List<ReceiveFailure<P>> tickChunkedMessages(P player) {
        List<ReceiveFailure<P>> failures = this.chunkedMessageManager().tick(player);
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
