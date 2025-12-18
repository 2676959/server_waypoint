//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.client.handlers.WaypointS2CPacketHandler;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.C2SPacketHandler;
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
        ModChatMessageHandler<String> handler = new ModChatMessageHandler<>(messageSender, permissionManager) {
            @Override
            public void onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
                super.onChatMessage(message, player, parameters);
            }
        };
        WaypointServerMod waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), handler);
        C2SPacketHandler<ServerCommandSource, ServerPlayerEntity> c2sPacketHandler = new C2SPacketHandler<>(messageSender, waypointServer);
        WaypointCommand waypointCommand = new WaypointCommand(waypointServer, messageSender, permissionManager);

        FabricLoader fabricLoader = FabricLoader.getInstance();
        if (fabricLoader.isModLoaded("fabric-permissions-api-v0")) {
            FabricPermissionManager.setFabricPermissionAPILoaded(true);
            LOGGER.info("found fabric-permissions-api, disable vanilla permission system");
        } else {
            LOGGER.info("fabric-permissions-api is not loaded, use vanilla permission system");
        }

        if (fabricLoader.isModLoaded("xaerominimap") || fabricLoader.isModLoaded("xaeroworldmap")) {
            Features.noXaerosMod = false;
            LOGGER.info("found xaero's mod, force disabling sendXaerosWorldId");
        } else {
            LOGGER.info("xaero's mod is not loaded, set sendXaerosWorldId to {} by config.json", CONFIG.Features().sendXaerosWorldId());
            //? if >= 1.20.5
            PayloadTypeRegistry.playS2C().register(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC);
        }

        // register waypoint command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> waypointCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(waypointServer::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> waypointServer.unload());
        // register chatMessageHandler
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        // register c2sPacketHandler
        registerPayloads();

        //? if >= 1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(UpdateRequestC2SPayload.ID, (handshakeC2SPayload, context) ->
                c2sPacketHandler.onClientUpdateRequest(context.player(), handshakeC2SPayload.clientUpdateRequestBuffer())
        );
        ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.ID, (clientHandshakeC2SPayload, context) ->
                c2sPacketHandler.onClientHandshake(context.player(), clientHandshakeC2SPayload.clientHandshakeBuffer())
        );
        //?} else if fabric {
        /*ServerPlayNetworking.registerGlobalReceiver(UpdateRequestC2SPayload.TYPE, (packet, player, responseSender) ->
                c2sPacketHandler.onClientUpdateRequest(player, packet.clientUpdateRequestBuffer()
                ));
        ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.TYPE, (packet, player, responseSender) ->
                c2sPacketHandler.onClientHandshake(player, packet.clientHandshakeBuffer()
                ));
        *///?}

        if (fabricLoader.getEnvironmentType() == EnvType.CLIENT) {
            registerClientHandlers();
        }
    }

    public static void registerPayloads() {
        //? if >= 1.20.5 {
        PayloadTypeRegistry.playS2C().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(UpdatesBundleS2CPayload.ID, UpdatesBundleS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC);

        PayloadTypeRegistry.playC2S().register(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateRequestC2SPayload.ID, UpdateRequestC2SPayload.PACKET_CODEC);
        //?}
    }

    private void registerClientHandlers() {
        //? if >= 1.20.5 {
        WaypointS2CPacketHandler.WaypointListHandler waypointListHandler = new WaypointS2CPacketHandler.WaypointListHandler();
        WaypointS2CPacketHandler.DimensionWaypointHandler dimensionWaypointHandler = new WaypointS2CPacketHandler.DimensionWaypointHandler();
        WaypointS2CPacketHandler.WorldWaypointHandler worldWaypointHandler = new WaypointS2CPacketHandler.WorldWaypointHandler();
        WaypointS2CPacketHandler.WaypointModificationHandler waypointModificationHandler = new WaypointS2CPacketHandler.WaypointModificationHandler();
        WaypointS2CPacketHandler.ServerHandshakeHandler serverHandshakeHandler = new WaypointS2CPacketHandler.ServerHandshakeHandler();
        WaypointS2CPacketHandler.UpdatesBundleHandler updatesBundleHandler = new WaypointS2CPacketHandler.UpdatesBundleHandler();

        ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.ID, waypointListHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.ID, dimensionWaypointHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.ID, worldWaypointHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.ID, waypointModificationHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(ServerHandshakeS2CPayload.ID, serverHandshakeHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(UpdatesBundleS2CPayload.ID, updatesBundleHandler::handle);
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
        return FabricLoader.getInstance().getConfigDir().resolve(ModInfo.MOD_ID);
    }
}
//?}