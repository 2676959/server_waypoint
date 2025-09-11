//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.fabric.permission.FabricPermissionManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Path;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.GROUP_ID;

public class ServerWaypointFabric implements DedicatedServerModInitializer, IPlatformConfigPath {
    @Override
    public void onInitializeServer() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        WaypointServerMod waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory());
        try {
            waypointServer.initServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            FabricPermissionManager.setFabricPermissionAPILoaded(true);
            LOGGER.info("found fabric-permissions-api, disable vanilla permission system");
        } else {
            LOGGER.info("fabric-permissions-api is not loaded, use vanilla permission system");
        }
        ModMessageSender sender = new ModMessageSender();
        FabricPermissionManager permissionManager = new FabricPermissionManager();
        WaypointCommand waypointCommand = new WaypointCommand(sender, permissionManager);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
            waypointCommand.register(dispatcher);
        });
        ModChatMessageHandler<String> handler = new ModChatMessageHandler<>(sender, permissionManager) {
            @Override
            public void onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
                super.onChatMessage(message, player, parameters);
            }
        };
        ClientHandshakeHandler<ServerCommandSource, ServerPlayerEntity> handshakeHandler = new ClientHandshakeHandler<>(sender);
        ServerLifecycleEvents.SERVER_STARTING.register(handler::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> waypointServer.saveAllFiles());
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID, ((handshakeC2SPayload, context) ->
                handshakeHandler.onHandshake(context.player(), handshakeC2SPayload.handshakeBuffer().edition())
        ));

        registerPayloads();
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC);
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(GROUP_ID);
    }
}
//?}