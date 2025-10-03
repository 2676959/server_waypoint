package _959.server_waypoint.common.client;

import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.core.WaypointListManager;
import _959.server_waypoint.core.WaypointsManagerCore;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

public class WaypointClient extends WaypointsManagerCore<WaypointListManager> {
    public static WaypointClient INSTANCE;
    
    public WaypointClient() {
        super();
        INSTANCE = this;
    }
    
    public static WaypointClient getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("WaypointClient has not been initialized");
        }
        return INSTANCE;
    }

    public static void onDimensionChange(String dimensionName) {
        WaypointListManager listManager = INSTANCE.getWaypointListManager(dimensionName);
        if (listManager != null) {
            for (WaypointList waypointList : listManager.getWaypointLists()) {
                waypointList.simpleWaypoints();
            }
        }
    }

    @Override
    protected WaypointListManager createWaypointListManager(String dimensionName) {
        return new WaypointListManager(dimensionName);
    }

    public void onWorldWaypointPayload(WorldWaypointS2CPayload payload) {
        WorldWaypointBuffer buffer = payload.worldWaypointBuffer();
        for (DimensionWaypointBuffer dimensionWaypoint : buffer.dimensionWaypointBuffers()) {
            WaypointListManager waypointListManager = this.addWaypointListManager(dimensionWaypoint.dimensionName());
            for (WaypointList list : dimensionWaypoint.waypointLists()) {
                waypointListManager.addWaypointList(list);
            }
        }
    }

    public void onWaypointModificationPayload(WaypointModificationS2CPayload payload) {
        WaypointModificationBuffer buffer = payload.waypointModification();
        String dimensionName = buffer.dimensionName();
        String listName = buffer.listName();
        SimpleWaypoint waypoint = buffer.waypoint();
        WaypointListManager waypointListManager = this.getWaypointListManager(dimensionName);
        switch (buffer.type()) {
            case ADD -> {
                if (waypointListManager == null) {
                    waypointListManager = this.addWaypointListManager(dimensionName);
                }
                WaypointList waypointList = waypointListManager.getWaypointListByName(listName);
                if (waypointList == null) {
                    waypointList = WaypointList.build(listName);
                    waypointListManager.addWaypointList(waypointList);
                }
                waypointList.add(waypoint);
            }
            case REMOVE -> {
                if (waypointListManager == null) {
                    return;
                }
                WaypointList waypointList = waypointListManager.getWaypointListByName(listName);
                if (waypointList == null) {
                    return;
                }
                waypointList.removeByName(waypoint.name());
            }
            case UPDATE -> {
                if (waypointListManager == null) {
                    return;
                }
                WaypointList waypointList = waypointListManager.getWaypointListByName(listName);
                if (waypointList == null) {
                    return;
                }
                SimpleWaypoint waypointFound = waypointList.getWaypointByName(waypoint.name());
                if (waypointFound == null) {
                    return;
                }
                waypointFound.copyFrom(waypoint);
            }
        }
    }
}
