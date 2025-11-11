//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import static _959.server_waypoint.fabric.ServerWaypointFabricServer.registerPayloads;

public class ServerWaypointFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
//        this.registerPayloadHandlers();


    }


}
//?}