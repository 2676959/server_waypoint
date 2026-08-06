package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointEditResultBufferCodec;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_EDIT_RESULT_CHANNEL;

public record WaypointEditResultBuffer(
        long requestId,
        EditResultStatus status,
        String dimensionName,
        String listIdentifier,
        String previousWaypointIdentifier,
        @Nullable SimpleWaypoint waypoint,
        int listRevision
) implements MessageBuffer {
    public WaypointEditResultBuffer {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        Objects.requireNonNull(previousWaypointIdentifier, "previousWaypointIdentifier");
        waypoint = waypoint == null ? null : new SimpleWaypoint(waypoint);
    }

    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_EDIT_RESULT_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointEditResultBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointEditResultBufferCodec.decode(byteBuf);
    }
}
