package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadTarget;

public interface MapModIntegration {
    UploadTarget uploadTarget();

    WaypointData collectUpload(UploadRequestBuffer request);

    boolean isEnabled(ClientConfig clientConfig);

    void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod);

    /** Whether the map mod is installed in this game. */
    boolean isInstalled();

    /** Whether the map mod can take waypoints now. */
    boolean isReady();

    /** Rewrites the waypoints this mod added to the map mod so they match the synced waypoints. */
    void syncAll(WaypointClientMod waypointClientMod);
}
