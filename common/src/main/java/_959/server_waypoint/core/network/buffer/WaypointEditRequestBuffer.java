package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointEditRequestBufferCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_EDIT_REQUEST_CHANNEL;

public record WaypointEditRequestBuffer(
        long requestId,
        String dimensionName,
        String listIdentifier,
        String waypointIdentifier,
        int expectedListRevision,
        WaypointPatch patch
) implements MessageBuffer {
    public WaypointEditRequestBuffer {
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        Objects.requireNonNull(waypointIdentifier, "waypointIdentifier");
        Objects.requireNonNull(patch, "patch");
    }

    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_EDIT_REQUEST_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointEditRequestBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointEditRequestBufferCodec.decode(byteBuf);
    }
}
