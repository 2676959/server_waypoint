package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

public class SimpleWaypointCodec {
    public static void encode(ByteBuf buf, SimpleWaypoint waypoint) {
        UtfStringCodec.encode(buf, waypoint.name());
        String initials = waypoint.initials();
        UtfStringCodec.encode(buf, initials);
        WaypointPos pos = waypoint.pos();
        buf.writeInt(pos.x());
        buf.writeInt(pos.y());
        buf.writeInt(pos.z());

        buf.writeByte(waypoint.colorIdx());

        int yaw = waypoint.yaw();
        buf.writeBoolean(yaw < 0);
        buf.writeByte(Math.abs(yaw));
        buf.writeBoolean(waypoint.global());
    }

    public static SimpleWaypoint decode(ByteBuf byteBuf) {
        String name = UtfStringCodec.decode(byteBuf);
        String initials = UtfStringCodec.decode(byteBuf);
        // pos
        int x = byteBuf.readInt();
        int y = byteBuf.readInt();
        int z = byteBuf.readInt();
        // colorIdx
        int colorIdx = byteBuf.readByte();
        // yaw
        boolean isNegative = byteBuf.readBoolean();
        byte b = byteBuf.readByte();
        int yaw = b & 0xFF; // get unsigned 8-bit int
        yaw = isNegative ? -yaw : yaw;
        // global
        boolean global = byteBuf.readBoolean();
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), colorIdx, yaw, global);
    }
}
