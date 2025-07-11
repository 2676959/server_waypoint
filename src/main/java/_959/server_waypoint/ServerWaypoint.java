package _959.server_waypoint;

import _959.server_waypoint.network.ChatMessageHandler;
import _959.server_waypoint.network.ClientHandshakeHandler;
import _959.server_waypoint.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.network.payload.s2c.SimpleWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.server.WaypointServer;
import _959.server_waypoint.server.command.WaypointCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerWaypoint implements DedicatedServerModInitializer {
	public static final String MOD_ID = "server_waypoint";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Initialize waypoint server.");
		WaypointServer waypointServer = new WaypointServer();
        try {
            waypointServer.initServer();
        } catch (IOException e) {
			LOGGER.error("Failed to initialize server waypoint", e);
            throw new RuntimeException(e);
        }
		this.registerPayloads();
		this.registerHandlers();
		CommandRegistrationCallback.EVENT.register(ServerWaypoint::registerCommands);
		ServerMessageEvents.CHAT_MESSAGE.register(ChatMessageHandler::onChatMessage);
    }

	public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment registrationEnvironment) {
		WaypointCommand.register(dispatcher);
	}

	private void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(SimpleWaypointS2CPayload.ID, SimpleWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC);
	}

	private void registerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID, ClientHandshakeHandler::onClientHandshake);
	}
}