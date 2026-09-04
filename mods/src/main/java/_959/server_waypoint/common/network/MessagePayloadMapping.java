package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.c2s.*;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.buffer.*;

public final class MessagePayloadMapping {
    private MessagePayloadMapping() {
    }

    public static ModPayload getPayload(SinglePacketMessage message, byte[] encodedMessage) {
        return switch (message.getChannelId()) {
            case MESSAGE_CHUNK_CHANNEL -> new MessageChunkS2CPayload(
                    (MessageChunkBuffer) message,
                    encodedMessage
            );
            case SERVER_HANDSHAKE_CHANNEL -> new ServerHandshakeS2CPayload((ServerHandshakeBuffer) message);
            case XAEROS_WORLD_ID_CHANNEL -> new XaerosWorldIdS2CPayload((XaerosWorldIdBuffer) message);
            case UPLOAD_REQUEST_CHANNEL -> new UploadRequestS2CPayload(
                    (UploadRequestBuffer) message,
                    encodedMessage
            );
            case CLIENT_HANDSHAKE_CHANNEL ->
                    throw new IllegalArgumentException("Cannot send a clientbound client-handshake payload");
            case UPLOAD_CHUNK_CHANNEL ->
                    throw new IllegalArgumentException("Cannot send a clientbound upload-chunk payload");
        };
    }
}
