package _959.server_waypoint.core;

import _959.server_waypoint.core.waypoint.WaypointList;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaypointListManager {
    protected String dimensionName;
    protected final Map<String, WaypointList> waypointListMap;

    protected WaypointListManager() {
        this.waypointListMap = new HashMap<>();
    }

    public WaypointListManager(String dimensionName) {
        this();
        this.dimensionName = dimensionName;
    }

    public boolean hasNoWaypoints() {
        for (WaypointList waypointList : this.waypointListMap.values()) {
            if (!waypointList.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return this.waypointListMap.isEmpty();
    }

    public String getDimensionName() {
        return this.dimensionName;
    }

    public List<WaypointList> getWaypointLists() {
        return new ArrayList<>(this.waypointListMap.values());
    }

    public Map<String, WaypointList> getWaypointListMap() {
        return this.waypointListMap;
    }

    public @Nullable WaypointList getWaypointListByName(String name) {
        return this.waypointListMap.get(name);
    }

    public void addWaypointList(WaypointList waypointList) {
        this.waypointListMap.put(waypointList.name(), waypointList);
    }

    public void removeWaypointListByName(String name) {
        this.waypointListMap.remove(name);
    }
}
