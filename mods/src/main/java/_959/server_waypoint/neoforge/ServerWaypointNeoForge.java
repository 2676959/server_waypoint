//? if neoforge {
package _959.server_waypoint.neoforge;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.neoforge.permission.NeoForgePermissionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

@Mod(ModInfo.MOD_ID)
public class ServerWaypointNeoForge implements IPlatformConfigPath {
    private final WaypointServerMod waypointServer;
    private final C2SPacketHandler<CommandSourceStack, ServerPlayer> c2sPacketHandler;
    private final WaypointCommand waypointCommand;
    private final ModChatMessageHandler<String> chatMessageHandler;

    public ServerWaypointNeoForge(IEventBus modEventBus) {
        ModMessageSender messageSender = ModMessageSender.getInstance();
        NeoForgePermissionManager permissionManager = new NeoForgePermissionManager();
        this.chatMessageHandler = new ModChatMessageHandler<>(messageSender, permissionManager) {};
        this.waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), this.chatMessageHandler);
        this.c2sPacketHandler = new C2SPacketHandler<>(messageSender, this.waypointServer);
        this.waypointCommand = new WaypointCommand(this.waypointServer, messageSender, permissionManager);

        this.configureLoadedMods();
        modEventBus.addListener(this::registerPayloads);
        if (isClientDist()) {
            ServerWaypointNeoForgeClient.initialize(modEventBus);
        }
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::listenChatMessages);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void configureLoadedMods() {
        if (ModList.get().isLoaded("xaerominimap") || ModList.get().isLoaded("xaeroworldmap")) {
            Features.noXaerosMod = false;
            LOGGER.info("found xaero's mod, force disabling sendXaerosWorldId");
        } else {
            LOGGER.info("xaero's mod is not loaded, set sendXaerosWorldId to {} by config.json", CONFIG.Features().sendXaerosWorldId());
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        this.waypointServer.load(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        this.waypointServer.unload();
    }

    private void listenChatMessages(ServerChatEvent event) {
        this.chatMessageHandler.onChatMessage(event.getPlayer(), event.getRawText());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // S2C
        if (isClientDist()) {
            ServerWaypointNeoForgeClient.registerClientPayloadHandlers(registrar);
        } else {
            registrar.playToClient(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(UpdatesBundleS2CPayload.ID, UpdatesBundleS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC, (payload, context) -> {});
        }
        if (Features.noXaerosMod) {
            registrar.playToClient(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC, (payload, context) -> {});
        }
        // C2S
        registrar.playToServer(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC, (payload, context) ->
                context.enqueueWork(() -> this.c2sPacketHandler.onClientHandshake((ServerPlayer) context.player(), payload.clientHandshakeBuffer()))
        );
        registrar.playToServer(UpdateRequestC2SPayload.ID, UpdateRequestC2SPayload.PACKET_CODEC, (payload, context) ->
                context.enqueueWork(() -> this.c2sPacketHandler.onClientUpdateRequest((ServerPlayer) context.player(), payload.clientUpdateRequestBuffer()))
        );
    }

    private void registerCommands(RegisterCommandsEvent event) {
        this.waypointCommand.register(event.getDispatcher());
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(ModInfo.MOD_ID);
    }

    private static boolean isClientDist() {
        try {
            return FMLEnvironment.class.getMethod("getDist").invoke(null) == Dist.CLIENT;
        } catch (NoSuchMethodException e) {
            try {
                return FMLEnvironment.class.getField("dist").get(null) == Dist.CLIENT;
            } catch (ReflectiveOperationException reflectiveException) {
                throw new IllegalStateException("Failed to determine NeoForge dist", reflectiveException);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to determine NeoForge dist", e);
        }
    }
}
//?}
