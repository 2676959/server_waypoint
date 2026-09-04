package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class SimpleWaypointCodec {
    private SimpleWaypointCodec() {
    }

    public static void encode(ByteBuf buf, SimpleWaypoint waypoint, EncodingContext context) {
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        UtfStringCodec.encode(buf, snapshot.name(), context);
        UtfStringCodec.encode(buf, snapshot.displayName(), context);
        String initials = snapshot.initials();
        UtfStringCodec.encode(buf, initials, context);
        WaypointPos pos = snapshot.pos();
        buf.writeInt(pos.x());
        buf.writeInt(pos.y());
        buf.writeInt(pos.z());
        buf.writeInt(snapshot.rgb());
        int yaw = snapshot.yaw();
        buf.writeBoolean(yaw < 0);
        buf.writeByte(Math.abs(yaw));
        buf.writeBoolean(snapshot.global());
        ListCodec.encode(buf, snapshot.keywords(), UtfStringCodec::encode, context);
        UtfStringCodec.encode(buf, snapshot.description(), context);
    }

    public static SimpleWaypoint decode(ByteBuf byteBuf, DecodingContext context) {
        String name = UtfStringCodec.decode(byteBuf, context);
        String displayName = UtfStringCodec.decode(byteBuf, context);
        String initials = UtfStringCodec.decode(byteBuf, context);
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
        List<String> keywords = ListCodec.decode(byteBuf, UtfStringCodec::decode, context);
        String description = UtfStringCodec.decode(byteBuf, context);
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
