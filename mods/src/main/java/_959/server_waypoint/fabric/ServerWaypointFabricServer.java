//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.MessageChunkC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.fabric.permission.FabricPermissionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        UploadCoordinator<ServerPlayer> uploadCoordinator = new UploadCoordinator<>(
                waypointServer,
                messageSender::sendPlayerMessage,
                messageSender::broadcastChunkedMessage,
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.upload(), CONFIG.CommandPermission().upload()),
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.uploadDelete(), CONFIG.CommandPermission().uploadDelete()),
                waypointServer.navigation().service()
        );
        C2SPacketHandler<CommandSourceStack, String, ServerPlayer> c2sPacketHandler = new C2SPacketHandler<>(
                messageSender,
                waypointServer,
                permissionManager,
                waypointServer.navigation().service(),
                uploadCoordinator
        );
        WaypointCommand waypointCommand = new WaypointCommand(waypointServer, messageSender, permissionManager, uploadCoordinator);

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
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            waypointServer.navigation().tick();
            messageSender.tickChunkedMessages();
        });
        ServerPlayConnectionEvents.JOIN.register(
                (listener, sender, server) -> waypointServer.navigation().onPlayerJoin(listener.player)
        );
        ServerPlayConnectionEvents.DISCONNECT.register(
                (listener, server) -> {
                    c2sPacketHandler.onDisconnect(listener.player);
                    waypointServer.navigation().onPlayerQuit(listener.player);
                }
        );
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> waypointServer.navigation().onPlayerRespawn(newPlayer)
        );
        // register chatMessageHandler
        ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);
        registerPayloads();

        //? if >= 1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.ID, (clientHandshakeC2SPayload, context) ->
                c2sPacketHandler.onClientHandshake(context.player(), clientHandshakeC2SPayload.clientHandshakeBuffer())
        );
        ServerPlayNetworking.registerGlobalReceiver(MessageChunkC2SPayload.ID, (payload, context) ->
                c2sPacketHandler.onMessageChunk(context.player(), payload.messageChunk())
        );
        //?} else if fabric {
        /*ServerPlayNetworking.registerGlobalReceiver(ClientHandshakeC2SPayload.ID, (packet, player, responseSender) ->
                c2sPacketHandler.onClientHandshake(player, packet.clientHandshakeBuffer()
                ));
        ServerPlayNetworking.registerGlobalReceiver(MessageChunkC2SPayload.ID, (packet, player, responseSender) ->
                c2sPacketHandler.onMessageChunk(player, packet.messageChunk()
                ));
        *///?}
    }

    public static void registerPayloads() {
        //? if >= 1.20.5 {
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(MessageChunkS2CPayload.ID, MessageChunkS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_s2c_registry_swap
        clientboundPlay
        ().register(UploadRequestS2CPayload.ID, UploadRequestS2CPayload.PACKET_CODEC);

        PayloadTypeRegistry.
        //$ payload_c2s_registry_swap
        serverboundPlay
        ().register(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.
        //$ payload_c2s_registry_swap
        serverboundPlay
        ().register(MessageChunkC2SPayload.ID, MessageChunkC2SPayload.PACKET_CODEC);
        //?}
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(ModInfo.MOD_ID);
    }
}
//?}
