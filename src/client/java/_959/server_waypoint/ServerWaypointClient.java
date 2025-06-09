package _959.server_waypoint;

import _959.server_waypoint.network.*;
import _959.server_waypoint.network.payload.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.SimpleWaypointS2CPayload;
import _959.server_waypoint.network.payload.WaypointListS2CPayload;
import _959.server_waypoint.network.payload.WorldWaypointS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerWaypointClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		this.registerPayloads();
		this.registerPayloadHandlers();
	}

	private void registerPayloadHandlers() {
		ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointListPayload));
		ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onDimensionWaypointPayload));
		ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onWorldWaypointPayload));
	}

	private void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(SimpleWaypointS2CPayload.ID, SimpleWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
	}
}