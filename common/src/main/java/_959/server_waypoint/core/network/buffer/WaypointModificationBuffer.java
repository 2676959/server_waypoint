package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.core.network.MessageChannelID.WAYPOINT_MODIFICATION_CHANNEL;

public record WaypointModificationBuffer(
        String dimensionName,
        String listName,
        Object waypointOrName,
        WaypointModificationType type,
        int syncId) implements MessageBuffer {

    @Nullable
    public SimpleWaypoint waypoint() {
        return switch (this.type) {
            case ADD, UPDATE, REMOVE -> (SimpleWaypoint) this.waypointOrName;
            case ADD_LIST, REMOVE_LIST -> null;
        };
    }

    public String waypointName() {
        if (waypointOrName instanceof String) {
            return (String) waypointOrName;
        } else if (waypointOrName instanceof SimpleWaypoint) {
            return ((SimpleWaypoint) waypointOrName).name();
        }
        return "";
    }

    @Override
    public MessageChannelID getChannelId() {
        return WAYPOINT_MODIFICATION_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        WaypointModificationBufferCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return WaypointModificationBufferCodec.decode(byteBuf);
    }
}