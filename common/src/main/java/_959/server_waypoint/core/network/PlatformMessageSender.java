package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import net.kyori.adventure.text.Component;

import static _959.server_waypoint.text.WaypointTextHelper.waypointTextNoTp;
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;
import static net.kyori.adventure.text.Component.text;

public interface PlatformMessageSender<S, P> {
    void sendMessage(S source, Component component);
    void sendPlayerMessage(P player, Component component);
    void sendError(S source, Component component);
    void sendPacket(S source, MessageBuffer packet);
    void sendPlayerPacket(P player, MessageBuffer packet);
    Iterable<? extends P> getBroadcastPlayers(S source);
    Component getSenderName(S source);

    default void broadcastWaypointModification(S source, WaypointModificationBuffer modification) {
        Component info = this.getModificationMessage(this.getSenderName(source), modification);
        for (P player : this.getBroadcastPlayers(source)) {
            this.sendPlayerMessage(player, info);
            this.sendPlayerPacket(player, modification);
        }
    }

    default void broadcastPacket(S source, MessageBuffer packet) {
        for (P player : this.getBroadcastPlayers(source)) {
            this.sendPlayerPacket(player, packet);
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
                Component.translatable("waypoint_list.modification.broadcast.player", senderName, modification.type().toTranslatable(), text(modification.listName()));
        };
    }
}
