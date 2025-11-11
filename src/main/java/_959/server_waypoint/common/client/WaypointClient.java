package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.render.WaypointRenderData;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.core.WaypointListManager;
import _959.server_waypoint.core.WaypointsManagerCore;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static _959.server_waypoint.common.client.render.WaypointRenderer.WaypointsOnHud;

public class WaypointClient extends WaypointsManagerCore<WaypointListManager> {
    public static final Logger LOGGER = LoggerFactory.getLogger("waypoint_client");
    public static WaypointClient INSTANCE;
    private static String currentDimensionName;
    private MinecraftClient mc;
    private Runnable screenUpdater;

    public void setMinecraftClient(MinecraftClient client) {
        this.mc = client;
    }
    
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

    public void setScreenUpdater(Runnable updater) {
        this.screenUpdater = updater;
    }

    public static void onDimensionChange(String dimensionName) {
        WaypointListManager waypointListManager = INSTANCE.fileManagerMap.get(dimensionName);
        currentDimensionName = dimensionName;
        if (waypointListManager == null) {
            WaypointsOnHud.clear();
            return;
        }
        final List<WaypointList> waypointLists = waypointListManager.getWaypointLists();
        WaypointsOnHud.clear();
        for (WaypointList waypointList : waypointLists) {
            String listName = waypointList.name();
            for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                WaypointRenderData renderData = WaypointRenderData.from(listName, waypoint);
                WaypointsOnHud.add(renderData);
            }
        }

    }

    @Override
    protected WaypointListManager createWaypointListManager(String dimensionName) {
        return new WaypointListManager(dimensionName);
    }

    public void onWorldWaypointPayload(WorldWaypointS2CPayload payload) {
        WorldWaypointBuffer buffer = payload.worldWaypointBuffer();
        this.fileManagerMap.clear();
        WaypointsOnHud.clear();
        currentDimensionName = this.mc.world.getRegistryKey().getValue().toString();
        LOGGER.info("this dimension: {}", currentDimensionName);
        boolean found = false;
        for (DimensionWaypointBuffer dimensionWaypoint : buffer.dimensionWaypointBuffers()) {
            String dimensionName = dimensionWaypoint.dimensionName();
            LOGGER.info("dimension name: {}", dimensionName);
            WaypointListManager waypointListManager = this.addWaypointListManager(dimensionName);
            if (!found && currentDimensionName.equals(dimensionName)) {
                found = true;
                for (WaypointList list : dimensionWaypoint.waypointLists()) {
                    String listName = list.name();
                    waypointListManager.addWaypointList(list);
                    for (SimpleWaypoint simpleWaypoint : list.simpleWaypoints()) {
                        WaypointRenderData renderData = WaypointRenderData.from(listName, simpleWaypoint);
                        WaypointsOnHud.add(renderData);
                    }
                }
            } else {
                for (WaypointList list : dimensionWaypoint.waypointLists()) {
                    waypointListManager.addWaypointList(list);
                }
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
        // update render data
        if (currentDimensionName.equals(dimensionName)) {
            WaypointsOnHud.clear();
            for (WaypointList waypointList : waypointListManager.getWaypointLists()) {
                String list = waypointList.name();
                for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
                    WaypointRenderData renderData = WaypointRenderData.from(list, simpleWaypoint);
                    WaypointsOnHud.add(renderData);
                }
            }
        }
    }
}
