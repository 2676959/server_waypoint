//? if fabric {
/*package _959.server_waypoint.fabric;

import _959.server_waypoint.ModInfo;
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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
            public void onChatMessage(PlayerChatMessage message, ServerPlayer player, ChatType.Bound parameters) {
                super.onChatMessage(message, player, parameters);
            }
        };
        WaypointServerMod waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), handler);
        C2SPacketHandler<CommandSourceStack, ServerPlayer> c2sPacketHandler = new C2SPacketHandler<>(messageSender, waypointServer);
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
            //? if >= 1.20.5 {
            PayloadTypeRegistry.
            //$ payload_s2c_registry_swap
            clientboundPlay
            ().register(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC);
            //?}
        }

        // register waypoint command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> waypointCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(waypointServer::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> waypointServer.unload());
        // register chatMessageHandler
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        registerPayloads();

        //? if >= 1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(UpdateRequestC2SPayload.ID, (handshakeC2SPayload, context) ->
                c2sPacketHandler.onClientUpdateRequest(context.player(), handshakeC2SPayload.clientUpdateRequestBuffer())
        );
        ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.ID, (clientHandshakeC2SPayload, context) ->
                c2sPacketHandler.onClientHandshake(context.player(), clientHandshakeC2SPayload.clientHandshakeBuffer())
        );
        //?} else if fabric {
        /^ServerPlayNetworking.registerGlobalReceiver(UpdateRequestC2SPayload.ID, (packet, player, responseSender) ->
                c2sPacketHandler.onClientUpdateRequest(player, packet.clientUpdateRequestBuffer()
                ));
        ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.ID, (packet, player, responseSender) ->
                c2sPacketHandler.onClientHandshake(player, packet.clientHandshakeBuffer()
                ));
        ^///?}
    }

    public static void registerPayloads() {
        //? if >= 1.20.5 {
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(UpdatesBundleS2CPayload.ID, UpdatesBundleS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC);

        PayloadTypeRegistry.
        //$ payload_c2s_registry_swap
        serverboundPlay
        ().register(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_c2s_registry_swap
        serverboundPlay
        ().register(UpdateRequestC2SPayload.ID, UpdateRequestC2SPayload.PACKET_CODEC);
        //?}
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(ModInfo.MOD_ID);
    }
}
*///?}
