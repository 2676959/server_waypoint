package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.upload.UploadTarget;

import java.util.List;

/** Client-thread coordination of automatic sync; pending work never retains old event payloads. */
final class MapModSyncCoordinator {
    private final List<MapModIntegration> integrations;
    private boolean pendingVoxelMapSync;

    MapModSyncCoordinator(List<MapModIntegration> integrations) {
        this.integrations = List.copyOf(integrations);
    }

    void onAutomaticSync(ClientWaypointSyncEvent event, WaypointClientMod client,
                         ClientConfig config, boolean integratedWorld) {
        if (event.type() == ClientWaypointSyncEvent.Type.ALL_SYNCED && integratedWorld) {
            return;
        }
        for (MapModIntegration integration : integrations) {
            synchronize(integration, event, client, config);
        }
    }

    void onUnchangedSync(WaypointClientMod client, ClientConfig config, boolean integratedWorld) {
        if (integratedWorld) {
            return;
        }
        for (MapModIntegration integration : integrations) {
            if (integration.uploadTarget() == UploadTarget.VOXELMAP) {
                synchronize(integration, ClientWaypointSyncEvent.allSynced(), client, config);
            }
        }
    }

    private void synchronize(MapModIntegration integration, ClientWaypointSyncEvent event,
                             WaypointClientMod client, ClientConfig config) {
        if (!integration.isInstalled() || !integration.isEnabled(config)) {
            return;
        }
        if (integration.uploadTarget() == UploadTarget.VOXELMAP) {
            if (!integration.isReady()) {
                pendingVoxelMapSync = true;
                return;
            }
            if (pendingVoxelMapSync) {
                pendingVoxelMapSync = false;
                integration.syncAll(client);
                return;
            }
        }
        integration.onClientWaypointSync(event, client);
    }

    void tick(WaypointClientMod client, ClientConfig config) {
        if (!pendingVoxelMapSync) {
            return;
        }
        for (MapModIntegration integration : integrations) {
            if (integration.uploadTarget() != UploadTarget.VOXELMAP) {
                continue;
            }
            if (!integration.isInstalled() || !integration.isEnabled(config)) {
                clearPending();
            } else if (integration.isReady()) {
                clearPending();
                integration.syncAll(client);
            }
        }
    }

    void clearPending() {
        pendingVoxelMapSync = false;
    }
}
