package _959.server_waypoint.core.network;

import _959.server_waypoint.ModInfo;

import static _959.server_waypoint.core.network.PayloadID.*;

public enum MessageChannelID {
    MESSAGE_CHUNK_CHANNEL(MESSAGE_CHUNK),
    SERVER_HANDSHAKE_CHANNEL(SERVER_HANDSHAKE),
    CLIENT_HANDSHAKE_CHANNEL(CLIENT_HANDSHAKE),
    UPLOAD_REQUEST_CHANNEL(UPLOAD_REQUEST),
    UPLOAD_CHUNK_CHANNEL(UPLOAD_CHUNK),
    XAEROS_WORLD_ID_CHANNEL("xaerominimap", "main");

    public final String ID;

    MessageChannelID(String packetId) {
        this(ModInfo.MOD_ID, packetId);
    }

    MessageChannelID(String namespace, String packetId) {
        this.ID = namespace + ":" + packetId;
    }

    @Override
    public String toString() {
        return this.ID;
    }
}
