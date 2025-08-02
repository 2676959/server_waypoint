package _959.server_waypoint.common.network;

import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import java.util.ArrayList;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.world.World;

public class WaypointCodecs {
    public static final PacketCodec<PacketByteBuf, SimpleWaypoint> SIMPLE_WAYPOINT;
    public static final PacketCodec<PacketByteBuf, WaypointList> WAYPOINT_LIST;
    public static final PacketCodec<RegistryByteBuf, DimensionWaypoint> DIMENSION_WAYPOINT;

    static {
        SIMPLE_WAYPOINT = PacketCodec.tuple(
                PacketCodecs.STRING, SimpleWaypoint::name,
                PacketCodecs.STRING, SimpleWaypoint::initials,
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, WaypointPos::x,
                        PacketCodecs.INTEGER, WaypointPos::y,
                        PacketCodecs.INTEGER, WaypointPos::z,
                        WaypointPos::new), SimpleWaypoint::pos,
                PacketCodecs.INTEGER, SimpleWaypoint::colorIdx,
                PacketCodecs.INTEGER, SimpleWaypoint::yaw,
                PacketCodecs.BOOLEAN, SimpleWaypoint::global,
                SimpleWaypoint::new);
        WAYPOINT_LIST = PacketCodec.tuple(
                PacketCodecs.STRING, WaypointList::name,
                PacketCodecs.collection(ArrayList::new, SIMPLE_WAYPOINT), WaypointList::simpleWaypoints,
                WaypointList::new);
        DIMENSION_WAYPOINT = PacketCodec.tuple(
                PacketCodecs.registryCodec(World.CODEC), DimensionWaypoint::dimKey,
                PacketCodecs.collection(ArrayList::new, WAYPOINT_LIST), DimensionWaypoint::waypointLists,
                DimensionWaypoint::new
        );
    }
}
