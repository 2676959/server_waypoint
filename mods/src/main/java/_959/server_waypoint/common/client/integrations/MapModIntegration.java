package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;

public interface MapModIntegration {
    boolean isEnabled(ClientConfig clientConfig);

    void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod);
}
