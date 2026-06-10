package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;

public final class XaerosMinimapIntegration implements MapModIntegration {
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToXaerosMinimap() && WaypointClientMod.isXaerosMinimapReady;
    }

    @Override
    public void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod) {
        switch (event.type()) {
            case ALL_SYNCED, WORLD_REPLACED -> XaerosMinimapWaypointHelper.replaceAll(waypointClientMod);
            case DIMENSION_REPLACED -> XaerosMinimapWaypointHelper.replaceDimension(event.dimensionName(), event.waypointLists());
            case LIST_REPLACED -> XaerosMinimapWaypointHelper.replaceList(event.dimensionName(), event.waypointList());
            case WAYPOINT_ADDED, WAYPOINT_REMOVED, WAYPOINT_UPDATED, LIST_ADDED, LIST_REMOVED ->
                    XaerosMinimapWaypointHelper.applyModification(
                            event.dimensionName(),
                            event.listName(),
                            event.modificationType(),
                            event.waypoint(),
                            event.waypointName()
                    );
        }
    }
}
