package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import net.kyori.adventure.text.Component;

public interface PlatformMessageSender<S, P> {
    void sendMessage(S source, Component component);
    void sendPlayerMessage(P player, Component component);
    void sendFeedback(S source, Component component, boolean broadcastToOps);
    void sendError(S source, Component component);
    void broadcastWaypointModification(S source, WaypointModificationBuffer modification);
    void sendPacket(S source, MessageBuffer packet);
    void sendPlayerPacket(P player, MessageBuffer packet);
}