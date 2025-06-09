package _959.server_waypoint.server.waypoint;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

public class WaypointList {
    private String name;
    private final List<SimpleWaypoint> simpleWaypoints;

    public static final PacketCodec<PacketByteBuf, WaypointList> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, WaypointList::name,
            PacketCodecs.collection(ArrayList::new, SimpleWaypoint.PACKET_CODEC), WaypointList::simpleWaypoints,
            WaypointList::new
    );

    private WaypointList(String name, List<SimpleWaypoint> simpleWaypoints) {
        this.name = name;
        this.simpleWaypoints = simpleWaypoints;
    }

    public String name() {
        return this.name;
    }

    public List<SimpleWaypoint> simpleWaypoints() {
        return this.simpleWaypoints;
    }

    public WaypointList setName(String name) {
        this.name = name;
        return this;
    }

    public WaypointList add(SimpleWaypoint waypoint) {
        this.simpleWaypoints.add(waypoint);
        return this;
    }

    public WaypointList remove(SimpleWaypoint waypoint) {
        this.simpleWaypoints.remove(waypoint);
        return this;
    }

    public WaypointList clear() {
        this.simpleWaypoints.clear();
        return this;
    }

    @Override
    public String toString() {
        return "WaypointList{" +
                "name='" + name + '\'' +
                ", simpleWaypoints=" + this.simpleWaypoints +
                '}';
    }

    public static WaypointList build(String name) {
        return new WaypointList(name, new ArrayList<>());
    }
}