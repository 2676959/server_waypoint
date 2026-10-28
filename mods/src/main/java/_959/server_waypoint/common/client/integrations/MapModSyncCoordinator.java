package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.upload.UploadTarget;

import java.util.EnumSet;
import java.util.List;

/** Client-thread coordination of automatic sync; pending work never retains old event payloads. */
final class MapModSyncCoordinator {
    private final List<MapModIntegration> integrations;
    private final EnumSet<UploadTarget> pendingSync = EnumSet.noneOf(UploadTarget.class);

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
            synchronize(integration, ClientWaypointSyncEvent.allSynced(), client, config);
        }
    }

    private void synchronize(MapModIntegration integration, ClientWaypointSyncEvent event,
                             WaypointClientMod client, ClientConfig config) {
        UploadTarget target = integration.uploadTarget();
        if (!integration.isInstalled() || !integration.isEnabled(config)) {
            pendingSync.remove(target);
            return;
        }
        if (!integration.isReady()) {
            pendingSync.add(target);
            return;
        }
        if (pendingSync.remove(target)) {
            integration.syncAll(client);
            return;
        }
        integration.onClientWaypointSync(event, client);
    }

    void tick(WaypointClientMod client, ClientConfig config) {
        if (pendingSync.isEmpty()) {
            return;
        }
        for (MapModIntegration integration : integrations) {
            UploadTarget target = integration.uploadTarget();
            if (!pendingSync.contains(target)) {
                continue;
            }
            if (!integration.isInstalled() || !integration.isEnabled(config)) {
                pendingSync.remove(target);
            } else if (integration.isReady()) {
                pendingSync.remove(target);
                integration.syncAll(client);
            }
        }
    }

    void clearPending() {
        pendingSync.clear();
    }
}
