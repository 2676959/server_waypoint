package _959.server_waypoint.core.waypoint;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WaypointList {
    @Expose @SerializedName("list_name")
    private String name;
    @Expose @SerializedName("waypoints")
    private final List<SimpleWaypoint> simpleWaypoints;

    public WaypointList(String name, List<SimpleWaypoint> simpleWaypoints) {
        this.name = name;
        this.simpleWaypoints = simpleWaypoints;
    }

    public @Nullable SimpleWaypoint getWaypointByName(String name) {
        return this.simpleWaypoints.stream().filter((waypoint) -> waypoint.name().equals(name)).findFirst().orElse(null);
    }

    public String name() {
        return this.name;
    }

    public int size() {
        return this.simpleWaypoints.size();
    }

    public boolean isEmpty() {
        return this.simpleWaypoints.isEmpty();
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

    public void removeByName(String name) {
        this.simpleWaypoints.removeIf(waypoint -> name.equals(waypoint.name()));

    }

    public WaypointList clear() {
        this.simpleWaypoints.clear();
        return this;
    }

    public String toString() {
        return "WaypointList{name='" + this.name + "', simpleWaypoints=" + this.simpleWaypoints + "}";
    }

    public static WaypointList build(String name) {
        return new WaypointList(name, new ArrayList<>());
    }
}
