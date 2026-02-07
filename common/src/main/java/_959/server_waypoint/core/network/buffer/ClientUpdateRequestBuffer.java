package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
import io.netty.buffer.ByteBuf;

import java.util.List;

import static _959.server_waypoint.core.network.MessageChannelID.CLIENT_UPDATE_REQUEST_CHANNEL;

/**
 * Third packet in the communication between client and server
 * */
public record ClientUpdateRequestBuffer(List<DimensionSyncIdentifier> dimensionSyncIds) implements MessageBuffer {
    @Override
    public MessageChannelID getChannelId() {
        return CLIENT_UPDATE_REQUEST_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        ClientUpdateRequestBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return ClientUpdateRequestBufferCodec.decode(byteBuf);
    }
}
