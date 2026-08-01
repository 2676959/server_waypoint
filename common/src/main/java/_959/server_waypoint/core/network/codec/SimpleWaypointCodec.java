package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class SimpleWaypointCodec {
    public static void encode(ByteBuf buf, SimpleWaypoint waypoint) {
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        UtfStringCodec.encode(buf, snapshot.name());
        UtfStringCodec.encode(buf, snapshot.displayName());
        String initials = snapshot.initials();
        UtfStringCodec.encode(buf, initials);
        WaypointPos pos = snapshot.pos();
        buf.writeInt(pos.x());
        buf.writeInt(pos.y());
        buf.writeInt(pos.z());
        buf.writeInt(snapshot.rgb());
        int yaw = snapshot.yaw();
        buf.writeBoolean(yaw < 0);
        buf.writeByte(Math.abs(yaw));
        buf.writeBoolean(snapshot.global());
        ListCodec.encode(buf, snapshot.keywords(), UtfStringCodec::encode);
        UtfStringCodec.encode(buf, snapshot.description());
    }

    public static SimpleWaypoint decode(ByteBuf byteBuf) {
        String name = UtfStringCodec.decode(byteBuf);
        String displayName = UtfStringCodec.decode(byteBuf);
        String initials = UtfStringCodec.decode(byteBuf);
        // pos
        int x = byteBuf.readInt();
        int y = byteBuf.readInt();
        int z = byteBuf.readInt();
        // rgb
        int rgb = byteBuf.readInt();
        // yaw
        boolean isNegative = byteBuf.readBoolean();
        byte b = byteBuf.readByte();
        int yaw = b & 0xFF; // get unsigned 8-bit int
        yaw = isNegative ? -yaw : yaw;
        // global
        boolean global = byteBuf.readBoolean();
        List<String> keywords = ListCodec.decode(byteBuf, UtfStringCodec::decode);
        String description = UtfStringCodec.decode(byteBuf);
        return new SimpleWaypoint(
                name,
                displayName,
                initials,
                new WaypointPos(x, y, z),
                rgb,
                yaw,
                global,
                keywords,
                description
        );
    }
}
