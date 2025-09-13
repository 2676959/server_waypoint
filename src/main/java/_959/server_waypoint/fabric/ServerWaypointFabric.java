//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import _959.server_waypoint.fabric.permission.FabricPermissionManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
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
import static _959.server_waypoint.core.WaypointServerCore.*;

public class ServerWaypointFabric implements DedicatedServerModInitializer, IPlatformConfigPath {
    @Override
    public void onInitializeServer() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        ModMessageSender messageSender = new ModMessageSender();
        FabricPermissionManager permissionManager = new FabricPermissionManager();
        WaypointCommand waypointCommand = new WaypointCommand(messageSender, permissionManager);
        ModChatMessageHandler<String> handler = new ModChatMessageHandler<>(messageSender, permissionManager) {
            @Override
            public void onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
                super.onChatMessage(message, player, parameters);
            }
        };
        ClientHandshakeHandler<ServerCommandSource, ServerPlayerEntity> handshakeHandler = new ClientHandshakeHandler<>(messageSender);
        WaypointServerMod waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), handler);
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

        if (FabricLoader.getInstance().isModLoaded("xaerominimap") || FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
            CONFIG.Features().sendXaerosWorldId(false);
            LOGGER.info("found xaero's mod, force disabling sendXaerosWorldId");
        } else {
            boolean enable = CONFIG.Features().sendXaerosWorldId();
            LOGGER.info("xaero's mod is not loaded, set sendXaerosWorldId to {} by config.json", enable);
            if (enable) {
                PayloadTypeRegistry.playS2C().register(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC);
                S2CPlayChannelEvents.REGISTER.register((event, packetSender, server, identifiers) -> {
                    if (CONFIG.Features().sendXaerosWorldId()) {
                        ServerPlayNetworking.send(event.getPlayer(), new XaerosWorldIdS2CPayload(new XaerosWorldIdBuffer(getWorldId())));
                    }
                });
            }
        }

        // register waypoint command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
            waypointCommand.register(dispatcher);
        });
        // pass MinecraftServer to waypointServer
        ServerLifecycleEvents.SERVER_STARTING.register(waypointServer::setMinecraftServer);
        // save files on shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            waypointServer.saveAllFiles();
            waypointServer.setMinecraftServer(null);
        });
        // register chatMessageHandler
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        // register handshakeHandler
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID, ((handshakeC2SPayload, context) ->
                handshakeHandler.onHandshake(context.player(), handshakeC2SPayload.handshakeBuffer().edition())
        ));
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