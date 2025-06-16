package _959.server_waypoint;

import _959.server_waypoint.network.*;
import _959.server_waypoint.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.SimpleWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WaypointModificationS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerWaypointClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
	private static boolean handshakeFinished = false;

    public static boolean isHandshakeFinished() {
        return handshakeFinished;
    }

    public static void setHandshakeFinished(boolean handshakeFinished) {
        ServerWaypointClient.handshakeFinished = handshakeFinished;
    }

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
		ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointModificationPayload));
	}

	private void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(SimpleWaypointS2CPayload.ID, SimpleWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);

		PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC);
	}
}