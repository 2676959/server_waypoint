package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointListUpdateBufferCodec;
import _959.server_waypoint.core.waypoint.WaypointList;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_LIST_UPDATE_CHANNEL;

public record WaypointListUpdateBuffer(
        String dimensionName,
        String previousListIdentifier,
        WaypointList waypointList
) implements MessageBuffer {
    public WaypointListUpdateBuffer {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(previousListIdentifier, "previousListIdentifier");
        waypointList = Objects.requireNonNull(waypointList, "waypointList").deepCopy();
    }

    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_LIST_UPDATE_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointListUpdateBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointListUpdateBufferCodec.decode(byteBuf);
    }
}
