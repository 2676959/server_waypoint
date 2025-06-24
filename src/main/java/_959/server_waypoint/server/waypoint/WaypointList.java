package _959.server_waypoint.server.waypoint;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

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

    @Nullable
    public SimpleWaypoint getWaypointByName(String name) {
        return this.simpleWaypoints.stream()
                .filter(waypoint -> waypoint.name().equals(name))
                .findFirst()
                .orElse(null);
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

    public void remove(SimpleWaypoint waypoint) {
        this.simpleWaypoints.remove(waypoint);
    }

    public List<SimpleWaypoint> removeByName(String name) {
        Iterator<SimpleWaypoint> iter = this.simpleWaypoints.iterator();
        List<SimpleWaypoint> removedWaypoints = new ArrayList<>();
        while (iter.hasNext()) {
            SimpleWaypoint waypoint = iter.next();
            if (name.equals(waypoint.name())) {
                removedWaypoints.add(waypoint);
                iter.remove();
            }
        }
        return removedWaypoints;
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