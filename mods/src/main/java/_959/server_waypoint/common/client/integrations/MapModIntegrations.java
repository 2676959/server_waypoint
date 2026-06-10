package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Public client-side API for pushing Server Waypoint's synced state into map mods.
 */
public final class MapModIntegrations {
    private static final List<MapModIntegration> INTEGRATIONS = createIntegrations();

    private MapModIntegrations() {
    }

    public static void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod waypointClientMod) {
        ClientConfig clientConfig = WaypointClientMod.getClientConfig();
        if (clientConfig == null) {
            return;
        }
        for (MapModIntegration integration : INTEGRATIONS) {
            if (integration.isEnabled(clientConfig)) {
                integration.onClientWaypointSync(event, waypointClientMod);
            }
        }
    }

    private static List<MapModIntegration> createIntegrations() {
        List<MapModIntegration> integrations = new ArrayList<>();
        integrations.add(new XaerosMinimapIntegration());
        //? if fabric
        integrations.add(new VoxelMapIntegration());
        return List.copyOf(integrations);
    }

    public static void syncXaerosMinimap(WaypointClientMod waypointClientMod) {
        if (!WaypointClientMod.isXaerosMinimapReady) {
            return;
        }
        XaerosMinimapWaypointHelper.replaceAll(waypointClientMod);
    }
}
