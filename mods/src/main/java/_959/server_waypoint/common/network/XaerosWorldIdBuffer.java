package _959.server_waypoint.common.network;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.SinglePacketMessage;
import io.netty.buffer.ByteBuf;

import static _959.server_waypoint.core.network.MessageChannelID.XAEROS_WORLD_ID_CHANNEL;

public record XaerosWorldIdBuffer(int id) implements SinglePacketMessage {
    @Override
    public MessageChannelID getChannelId() {
        return XAEROS_WORLD_ID_CHANNEL;
    }

    @Override
    public void encode(ByteBuf byteBuf) {
        XaerosWorldIdBufferCodec.encode(byteBuf, this);
    }
}
