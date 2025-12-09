//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.fabric.permission.FabricPermissionManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;

//? if >= 1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;


import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.*;

public class ServerWaypointFabricServer implements ModInitializer, IPlatformConfigPath {

    @Override
    public void onInitialize() {
        ModMessageSender messageSender = ModMessageSender.getInstance();
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

        if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            FabricPermissionManager.setFabricPermissionAPILoaded(true);
            LOGGER.info("found fabric-permissions-api, disable vanilla permission system");
        } else {
            LOGGER.info("fabric-permissions-api is not loaded, use vanilla permission system");
        }

        if (FabricLoader.getInstance().isModLoaded("xaerominimap") || FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
            Features.noXaerosMod = false;
            LOGGER.info("found xaero's mod, force disabling sendXaerosWorldId");
        } else {
            LOGGER.info("xaero's mod is not loaded, set sendXaerosWorldId to {} by config.json", CONFIG.Features().sendXaerosWorldId());
            //? if >= 1.20.5
            PayloadTypeRegistry.playS2C().register(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC);
        }

        // register waypoint command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> waypointCommand.register(dispatcher));
        // pass MinecraftServer to waypointServer
        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
            waypointServer.setMinecraftServer(minecraftServer);
            if (minecraftServer.isDedicated()) {
                try {
                    waypointServer.initServer();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                if (waypointServer.isDataRootPathSet()) {
                    waypointServer.changeDataRootPath(minecraftServer.getSavePath(WorldSavePath.ROOT));
                } else {
                    waypointServer.setDataRootPath(minecraftServer.getSavePath(WorldSavePath.ROOT));
                    try {
                        waypointServer.initServer();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        // save files on shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            waypointServer.saveAllFiles();
            waypointServer.setMinecraftServer(null);
        });
        // register chatMessageHandler
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        // register handshakeHandler
        registerPayloads();

        //? if >= 1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.ID, ((handshakeC2SPayload, context) ->
                handshakeHandler.onHandshake(context.player(), handshakeC2SPayload.handshakeBuffer().edition())
        ));
        //?} else if fabric {
        /*ServerPlayNetworking.registerGlobalReceiver(HandshakeC2SPayload.TYPE, (packet, player, responseSender) ->
                handshakeHandler.onHandshake(player, packet.handshakeBuffer().edition()
                ));
        *///?}

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClientHandlers();
        }
    }

    public static void registerPayloads() {
        //? if >= 1.20.5 {
        PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC);
        //?}
    }

    private void registerClientHandlers() {
        //? if >= 1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointListPayload));
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onDimensionWaypointPayload));
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.ID, (ServerWaypointPayloadHandler::onWorldWaypointPayload));
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.ID, (ServerWaypointPayloadHandler::onWaypointModificationPayload));
        //?} else if fabric {
        /*ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWaypointListPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onDimensionWaypointPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWorldWaypointPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWaypointModificationPayload(packet, player));
        *///?}
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(GROUP_ID);
    }
}
//?}