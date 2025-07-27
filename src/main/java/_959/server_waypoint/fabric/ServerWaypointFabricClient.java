//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import static _959.server_waypoint.fabric.ServerWaypointFabric.registerPayloads;

public class ServerWaypointFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        registerPayloads();
        this.registerPayloadHandlers();
    }

    private void registerPayloadHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointListPayload));
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onDimensionWaypointPayload));
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onWorldWaypointPayload));
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointModificationPayload));
    }
}
//?}