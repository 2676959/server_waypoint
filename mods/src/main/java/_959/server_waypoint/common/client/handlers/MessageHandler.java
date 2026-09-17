package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.core.network.buffer.*;

/**
 * handle buffer received on the client side
 * */
public interface MessageHandler {
    void onServerHandshake(ServerHandshakeBuffer buffer);
    void onMessageChunk(MessageChunkBuffer buffer);
}
