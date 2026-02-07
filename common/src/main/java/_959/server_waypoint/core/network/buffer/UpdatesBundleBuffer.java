package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.UpdatesBundleCodec;
import io.netty.buffer.ByteBuf;

import java.util.Collection;

import static _959.server_waypoint.core.network.MessageChannelID.UPDATES_BUNDLE_CHANNEL;

/**
 * Fourth packet in the communication between client and server
 * */
public class UpdatesBundleBuffer extends DimensionWaypointsList {
    public UpdatesBundleBuffer() {
        super();
    }

    public UpdatesBundleBuffer(Collection<DimensionWaypointBuffer> collection) {
        super(collection);
    }

    @Override
    public MessageChannelID getChannelId() {
        return UPDATES_BUNDLE_CHANNEL;
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return UpdatesBundleCodec.decode(byteBuf);
    }
}