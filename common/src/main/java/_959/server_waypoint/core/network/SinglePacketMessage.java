package _959.server_waypoint.core.network;

import io.netty.buffer.ByteBuf;

/** A message whose encoded form must fit in one cross-platform packet. */
public interface SinglePacketMessage extends NetworkMessage {
    MessageChannelID getChannelId();

    void encode(ByteBuf buffer);
}
