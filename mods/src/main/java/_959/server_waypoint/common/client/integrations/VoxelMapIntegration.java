//? if fabric {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;

public final class VoxelMapIntegration implements MapModIntegration {
    @Override
    public boolean isEnabled(ClientConfig clientConfig) {
        return clientConfig.isAutoSyncToVoxelMap();
    }

    @Override
    public void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod) {
        switch (event.type()) {
            case ALL_SYNCED, WORLD_REPLACED -> VoxelMapWaypointHelper.replaceAll(waypointClientMod);
            case DIMENSION_REPLACED -> VoxelMapWaypointHelper.replaceDimension(event.dimensionName(), event.waypointLists());
            case LIST_REPLACED -> VoxelMapWaypointHelper.replaceList(event.dimensionName(), event.waypointList());
            case WAYPOINT_ADDED, WAYPOINT_REMOVED, WAYPOINT_UPDATED, LIST_ADDED, LIST_REMOVED ->
                    VoxelMapWaypointHelper.applyModification(
                            event.dimensionName(),
                            event.listName(),
                            event.modificationType(),
                            event.waypoint(),
                            event.waypointName()
                    );
        }
    }
}
//?}
