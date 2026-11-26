package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.upload.UploadTarget;

import java.util.EnumSet;
import java.util.List;

/** Client-thread coordination of automatic sync; pending work never retains old event payloads. */
final class MapModSyncCoordinator {
    private static final ClientWaypointSyncEvent FULL_SYNC = ClientWaypointSyncEvent.allSynced();
    private static final int NOTIFICATION_WAIT_TICKS = 20;

    private final List<MapModIntegration> integrations;
    private final MapModSyncNotifications notifications;
    private final EnumSet<UploadTarget> pendingSync = EnumSet.noneOf(UploadTarget.class);
    private int notificationWaitTicks;

    MapModSyncCoordinator(List<MapModIntegration> integrations) {
        this(integrations, new MapModSyncNotifications());
    }

    MapModSyncCoordinator(List<MapModIntegration> integrations, MapModSyncNotifications notifications) {
        this.integrations = List.copyOf(integrations);
        this.notifications = notifications;
    }

    void onAutomaticSync(ClientWaypointSyncEvent event, WaypointClientMod client,
                         ClientConfig config, boolean integratedWorld) {
        if (event.type() == ClientWaypointSyncEvent.Type.ALL_SYNCED && integratedWorld) {
            return;
        }
        flushNotifications();
        try {
            for (MapModIntegration integration : integrations) {
                synchronize(integration, event, client, config);
            }
        } finally {
            if (pendingSync.isEmpty()) {
                flushNotifications();
            }
        }
    }

    void onUnchangedSync(WaypointClientMod client, ClientConfig config, boolean integratedWorld) {
        onAutomaticSync(FULL_SYNC, client, config, integratedWorld);
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
        ClientWaypointSyncEvent syncEvent = pendingSync.remove(target) ? FULL_SYNC : event;
        notifications.recordSuccess(target, integration.onClientWaypointSync(syncEvent, client));
    }

    void tick(WaypointClientMod client, ClientConfig config) {
        if (pendingSync.isEmpty()) {
            return;
        }
        try {
            for (MapModIntegration integration : integrations) {
                if (pendingSync.contains(integration.uploadTarget())) {
                    synchronize(integration, FULL_SYNC, client, config);
                }
            }
        } finally {
            if (pendingSync.isEmpty() || (notifications.hasSuccess() && ++notificationWaitTicks >= NOTIFICATION_WAIT_TICKS)) {
                flushNotifications();
            }
        }
    }

    private void flushNotifications() {
        notificationWaitTicks = 0;
        notifications.flush();
    }

    void clearPending() {
        pendingSync.clear();
        notificationWaitTicks = 0;
        notifications.clear();
    }
}
