package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.upload.UploadTarget;

import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public client-side API for syncing Server Waypoint state with supported map mods.
 */
public final class MapModIntegrations {
    private static final List<MapModIntegration> INTEGRATIONS = createIntegrations();
    private static final MapModSyncCoordinator AUTOMATIC_SYNC = new MapModSyncCoordinator(INTEGRATIONS);

    private MapModIntegrations() {
    }

    public static void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod) {
        ClientConfig clientConfig = WaypointClientMod.getClientConfig();
        if (clientConfig == null) {
            return;
        }
        AUTOMATIC_SYNC.onAutomaticSync(event, waypointClientMod, clientConfig, WaypointServerMod.runsWithClient());
    }

    /** The mod cache can be current while VoxelMap's separate marker file is stale. */
    public static void onUnchangedClientWaypointSync(WaypointClientMod waypointClientMod) {
        ClientConfig clientConfig = WaypointClientMod.getClientConfig();
        if (clientConfig != null) {
            AUTOMATIC_SYNC.onUnchangedSync(waypointClientMod, clientConfig, WaypointServerMod.runsWithClient());
        }
    }

    public static void tickPendingSync(WaypointClientMod waypointClientMod) {
        ClientConfig clientConfig = WaypointClientMod.getClientConfig();
        if (clientConfig == null || WaypointClientMod.getNetworkState() != WaypointClientMod.ClientNetworkState.SYNC_FINISHED) {
            AUTOMATIC_SYNC.clearPending();
            return;
        }
        AUTOMATIC_SYNC.tick(waypointClientMod, clientConfig);
    }

    public static void clearPendingSync() {
        AUTOMATIC_SYNC.clearPending();
    }

    /** Manual recovery ignores auto-sync preferences and always rewrites installed, ready maps. */
    public static void onClientWaypointDownload(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod) {
        for (MapModIntegration integration : INTEGRATIONS) {
            if (integration.isInstalled() && integration.isReady()) {
                integration.onClientWaypointSync(event, waypointClientMod);
            }
        }
    }

    /** The integration for {@code target}, or empty when this loader has none. */
    public static Optional<MapModIntegration> find(UploadTarget target) {
        return INTEGRATIONS.stream()
                .filter(integration -> integration.uploadTarget() == target)
                .findFirst();
    }

    /** Collects a detached snapshot; callers must run this on the Minecraft client thread. */
    public static WaypointData collectUpload(UploadRequestBuffer request) {
        return find(request.target())
                .map(integration -> integration.collectUpload(request))
                .orElseGet(() -> WaypointData.upload(request.requestId(), switch (request.target()) {
                    case XAERO -> UploadStatus.XAERO_NOT_INSTALLED;
                    case VOXELMAP -> UploadStatus.VOXELMAP_NOT_INSTALLED;
                }, List.of()));
    }

    private static List<MapModIntegration> createIntegrations() {
        List<MapModIntegration> integrations = new ArrayList<>();
        integrations.add(new XaerosMinimapIntegration());
        //? if voxelmap
        integrations.add(new VoxelMapIntegration());
        return List.copyOf(integrations);
    }

    /**
     * Rewrites the waypoints this mod added to a map mod, if this loader supports it and it's
     * installed and ready. Returns whether it ran.
     */
    public static boolean syncNow(UploadTarget target, WaypointClientMod waypointClientMod) {
        Optional<MapModIntegration> integration = find(target)
                .filter(MapModIntegration::isInstalled)
                .filter(MapModIntegration::isReady);
        integration.ifPresent(mapMod -> mapMod.syncAll(waypointClientMod));
        return integration.isPresent();
    }
}
