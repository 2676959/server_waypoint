package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WaypointList {
    public static final int REMOVE_LIST = -2;
    @Expose @SerializedName("list_name") private String name;
    @Expose @SerializedName("n") private int syncNum;
    @Expose @SerializedName("waypoints") private List<SimpleWaypoint> simpleWaypoints;
    private boolean show = true;
    private boolean expand = true;

    public WaypointList() {
        this.simpleWaypoints = new ArrayList<>();
    }

    public WaypointList(String name, int syncNum, List<SimpleWaypoint> simpleWaypoints) {
        this.name = name;
        this.syncNum = syncNum;
        this.simpleWaypoints = simpleWaypoints;
    }

    public @Nullable SimpleWaypoint getWaypointByName(String name) {
        return this.simpleWaypoints.stream().filter((waypoint) -> waypoint.name().equals(name)).findFirst().orElse(null);
    }

    public boolean isShow() {
        return this.show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public boolean isExpand() {
        return this.expand;
    }

    public void setExpand(boolean expand) {
        this.expand = expand;
    }

    public String name() {
        return this.name;
    }

    public int getSyncNum() {
        return this.syncNum;
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

    public WaypointListSyncIdentifier getIdentifier() {
        return new WaypointListSyncIdentifier(this.name, this.syncNum);
    }

    /**
     * does not increment syncNum
     * */
    public void addByClient(SimpleWaypoint waypoint) {
        this.simpleWaypoints.add(waypoint);
    }

    /**
     * increment syncNum
     * */
    public void addByServer(SimpleWaypoint waypoint) {
        this.simpleWaypoints.add(waypoint);
        this.syncNum++;
    }

    /**
     * increment syncNum
     * */
    public void remove(SimpleWaypoint waypoint) {
        this.simpleWaypoints.remove(waypoint);
        this.syncNum++;
    }

    /**
     * does not increment syncNum, only used by client
     * */
    public void removeByName(String name) {
        this.simpleWaypoints.removeIf(waypoint -> name.equals(waypoint.name()));
    }

    public void incSyncNum() {
        this.syncNum++;
    }

    public WaypointList clear() {
        this.simpleWaypoints.clear();
        return this;
    }

    public WaypointList deepCopy() {
        WaypointList newList = build(this.name, this.syncNum);
        for (SimpleWaypoint waypoint : this.simpleWaypoints) {
            newList.addByClient(new SimpleWaypoint(waypoint));
        }
        return newList;
    }

    public String toString() {
        return "WaypointList{name='" + this.name + "', simpleWaypoints=" + this.simpleWaypoints + "}";
    }

    public static WaypointList build(String name, int syncId) {
        return new WaypointList(name, syncId, new ArrayList<>());
    }

    public static WaypointList buildByServer(String name) {
        return new WaypointList(name, 1, new ArrayList<>());
    }

    public static WaypointList buildByClient(String name) {
        return new WaypointList(name, 0, new ArrayList<>());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WaypointList other = (WaypointList) o;
        return this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
}
