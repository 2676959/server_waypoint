package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import net.kyori.adventure.text.Component;

import java.util.List;

import static _959.server_waypoint.text.WaypointTextHelper.waypointTextNoTp;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;
import static _959.server_waypoint.text.FormattedTextHelper.parse;

public interface PlatformMessageSender<S, P> {
    void sendMessage(S source, Component component);
    void sendPlayerMessage(P player, Component component);
    void sendError(S source, Component component);
    void sendPacket(S source, MessageBuffer packet);
    void sendPlayerPacket(P player, MessageBuffer packet);
    void broadcastPacket(MessageBuffer packet);
    Iterable<? extends P> getBroadcastPlayers(S source);
    default Iterable<? extends P> getBroadcastPlayersFromPlayer(P player) {
        return List.of(player);
    }
    Component getSenderName(S source);

    default void broadcastWaypointModification(S source, WaypointModificationBuffer modification) {
        Component info = this.getModificationMessage(this.getSenderName(source), modification);
        for (P player : this.getBroadcastPlayers(source)) {
            this.sendPlayerMessage(player, info);
            this.sendPlayerPacket(player, modification);
        }
    }

    default void broadcastPacketFromPlayer(P player, MessageBuffer packet) {
        for (P recipient : this.getBroadcastPlayersFromPlayer(player)) {
            this.sendPlayerPacket(recipient, packet);
        }
    }

    default Component getModificationMessage(Component senderName, WaypointModificationBuffer modification) {
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
