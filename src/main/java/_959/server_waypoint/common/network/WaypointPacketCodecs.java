package _959.server_waypoint.common.network;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.codec.DimensionWaypointCodec;
import _959.server_waypoint.core.network.codec.SimpleWaypointCodec;
import _959.server_waypoint.core.network.codec.WaypointListBufferCodec;
import _959.server_waypoint.core.network.codec.WorldWaypointBufferCodec;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;

public class WaypointPacketCodecs {
    public static final PacketCodec<ByteBuf, SimpleWaypoint> SIMPLE_WAYPOINT;
    public static final PacketCodec<ByteBuf, WaypointListBuffer> WAYPOINT_LIST_BUFFER;
    public static final PacketCodec<ByteBuf, DimensionWaypoint> DIMENSION_WAYPOINT;
    public static final PacketCodec<ByteBuf, WorldWaypointBuffer> WORLD_WAYPOINT_BUFFER;

    static {
        SIMPLE_WAYPOINT = new PacketCodec<>() {
            @Override
            public void encode(ByteBuf buf, SimpleWaypoint value) {
                SimpleWaypointCodec.encode(buf, value);
            }

            @Override
            public SimpleWaypoint decode(ByteBuf buf) {
                return SimpleWaypointCodec.decode(buf);
            }
        };
        WAYPOINT_LIST_BUFFER = new PacketCodec<>() {
            @Override
            public void encode(ByteBuf buf, WaypointListBuffer value) {
                WaypointListBufferCodec.encode(buf, value);
            }

            @Override
            public WaypointListBuffer decode(ByteBuf buf) {
                return WaypointListBufferCodec.decode(buf);
            }
        };

        DIMENSION_WAYPOINT = new PacketCodec<>() {
            @Override
            public void encode(ByteBuf buf, DimensionWaypoint value) {
                DimensionWaypointCodec.encode(buf, value);
            }

            @Override
            public DimensionWaypoint decode(ByteBuf buf) {
                return DimensionWaypointCodec.decode(buf);
            }
        };

        WORLD_WAYPOINT_BUFFER = new PacketCodec<>() {
            @Override
            public void encode(ByteBuf buf, WorldWaypointBuffer value) {
                WorldWaypointBufferCodec.encode(buf, value);
            }

            @Override
            public WorldWaypointBuffer decode(ByteBuf buf) {
                return WorldWaypointBufferCodec.decode(buf);
            }
        };

//        Deprecated
//        SIMPLE_WAYPOINT = PacketCodec.tuple(
//                PacketCodecs.STRING, SimpleWaypoint::name,
//                PacketCodecs.STRING, SimpleWaypoint::initials,
//                PacketCodec.tuple(
//                        PacketCodecs.INTEGER, WaypointPos::x,
//                        PacketCodecs.INTEGER, WaypointPos::y,
//                        PacketCodecs.INTEGER, WaypointPos::z,
//                        WaypointPos::new), SimpleWaypoint::pos,
//                PacketCodecs.INTEGER, SimpleWaypoint::colorIdx,
//                PacketCodecs.INTEGER, SimpleWaypoint::yaw,
//                //? if <= 1.21.3 {
//                /*PacketCodecs.BOOL,
//                *///?} elif >= 1.21.5 {
//                PacketCodecs.BOOLEAN,
//                //?}
//                SimpleWaypoint::global,
//                SimpleWaypoint::new);

//        WAYPOINT_LIST = PacketCodec.tuple(
//                PacketCodecs.STRING, WaypointList::name,
//                PacketCodecs.collection(ArrayList::new, SIMPLE_WAYPOINT), WaypointList::simpleWaypoints,
//                WaypointList::new);

//        DIMENSION_WAYPOINT = PacketCodec.tuple(
//                PacketCodecs.STRING, DimensionWaypoint::dimString,
//                PacketCodecs.collection(ArrayList::new, WAYPOINT_LIST_BUFFER), DimensionWaypoint::waypointLists,
//                DimensionWaypoint::new
//        );
    }
}
