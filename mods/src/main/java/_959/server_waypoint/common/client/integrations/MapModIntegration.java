package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.upload.UploadTarget;

public interface MapModIntegration {
    UploadTarget uploadTarget();

    void uploadToServer(UploadRequestBuffer request);

    boolean isEnabled(ClientConfig clientConfig);

    void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod);
}
