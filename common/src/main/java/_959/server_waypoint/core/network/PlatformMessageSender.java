package _959.server_waypoint.core.network;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
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
        try {
            return this.chunkedMessageManager().send(
                player,
                message,
                CONFIG.Features().compressChunkedMessages(),
                packet -> this.sendPlayerPacket(player, packet)
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
        } catch (RuntimeException exception) {
            this.chunkedMessageManager().clear(player);
            WaypointServerCore.LOGGER.warn(
                    "Failed to deliver chunked message type {}",
                    message.getClass().getSimpleName(),
                    exception
            );
            return ChunkedMessageSendResult.DELIVERY_FAILED;
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

    default List<ChunkedMessage> receiveChunkedMessage(P player, MessageChunkBuffer packet) {
        return this.receiveChunkedMessage(
                player,
                packet,
                () -> this.onChunkedMessageSequenceFailure(player)
        );
    }

    default List<ChunkedMessage> receiveChunkedMessage(
            P player,
            MessageChunkBuffer packet,
            Runnable orderingFailureHandler
    ) {
        return this.chunkedMessageManager().receive(
                player,
                packet,
                response -> this.sendPlayerPacket(player, response),
                orderingFailureHandler
        );
    }

    default void tickChunkedMessages() {
        this.chunkedMessageManager().tick();
    }

    default void disconnectChunkedMessages(P player) {
        this.chunkedMessageManager().clear(player);
    }

    default void onChunkedMessageSequenceFailure(P player) {
        this.sendPlayerMessage(
                player,
                Component.translatable("waypoint.network.resynchronizing")
        );
    }

    private ChunkedMessageManager<P> chunkedMessageManager() {
        return ChunkedMessageManagerRegistry.get(this);
    }

    default void broadcastWaypointModification(S source, WaypointModificationMessage modification) {
        Component info = this.getModificationMessage(this.getSenderName(source), modification);
        for (P player : this.getBroadcastPlayers(source)) {
            this.sendPlayerMessage(player, info);
            this.sendPlayerChunkedMessage(player, modification);
        }
    }

    default void broadcastChunkedMessageFromPlayer(P player, ChunkedMessage message) {
        for (P recipient : this.getBroadcastPlayersFromPlayer(player)) {
            this.sendPlayerChunkedMessage(recipient, message);
        }
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
