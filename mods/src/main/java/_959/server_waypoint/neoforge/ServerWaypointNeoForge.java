//? if neoforge {
/*package _959.server_waypoint.neoforge;

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
//? if >= 1.20.5 {
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//?} elif = 1.20.4 {
/^import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
^///?} elif = 1.20.2 {
/^import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;
^///?}
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

@Mod(ModInfo.MOD_ID)
public class ServerWaypointNeoForge implements IPlatformConfigPath {
    private static final String NETWORK_PROTOCOL_VERSION = "1";
//? if = 1.20.2 {
    /^public static final SimpleChannel PACKET_CHANNEL = NetworkRegistry.newSimpleChannel(
            _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, "main"),
            () -> NETWORK_PROTOCOL_VERSION,
            NETWORK_PROTOCOL_VERSION::equals,
            NETWORK_PROTOCOL_VERSION::equals
    );
    ^///?}

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
//? if = 1.20.2 {
        /^this.registerPayloads();
^///?} else {
        modEventBus.addListener(this::registerPayloads);
//?}
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

//? if >= 1.20.5 {
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_PROTOCOL_VERSION);
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
//?} elif = 1.20.4 {
    /^private void registerPayloads(RegisterPayloadHandlerEvent event) {
        final IPayloadRegistrar registrar = event.registrar(ModInfo.MOD_ID).versioned(NETWORK_PROTOCOL_VERSION);
        if (isClientDist()) {
            ServerWaypointNeoForgeClient.registerClientPayloadHandlers(registrar);
        } else {
            registerNoopClientPayloadHandlers(registrar);
        }
        registrar.play(ClientHandshakeC2SPayload.CLIENT_HANDSHAKE_PAYLOAD, ClientHandshakeC2SPayload::new, handler ->
                handler.server((payload, context) -> context.workHandler().execute(() -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        this.c2sPacketHandler.onClientHandshake(player, payload.clientHandshakeBuffer());
                    }
                }))
        );
        registrar.play(UpdateRequestC2SPayload.CLIENT_UPDATE_REQUEST_PAYLOAD, UpdateRequestC2SPayload::new, handler ->
                handler.server((payload, context) -> context.workHandler().execute(() -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        this.c2sPacketHandler.onClientUpdateRequest(player, payload.clientUpdateRequestBuffer());
                    }
                }))
        );
    }

    private static void registerNoopClientPayloadHandlers(IPayloadRegistrar registrar) {
        registrar.play(WaypointListS2CPayload.WAYPOINT_LIST_PAYLOAD_ID, WaypointListS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(DimensionWaypointS2CPayload.DIM_WAYPOINT_PAYLOAD_ID, DimensionWaypointS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(WorldWaypointS2CPayload.WORLD_WAYPOINT_PAYLOAD_ID, WorldWaypointS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(WaypointModificationS2CPayload.WAYPOINT_MODIFICATION_PAYLOAD_ID, WaypointModificationS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(UpdatesBundleS2CPayload.UPDATES_BUNDLE_PAYLOAD_ID, UpdatesBundleS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(ServerHandshakeS2CPayload.SERVER_HANDSHAKE_PAYLOAD, ServerHandshakeS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        if (Features.noXaerosMod) {
            registrar.play(XaerosWorldIdS2CPayload.XAEROS_WORLD_ID_PAYLOAD_ID, XaerosWorldIdS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        }
    }
^///?} elif = 1.20.2 {
    /^private void registerPayloads() {
        if (isClientDist()) {
            ServerWaypointNeoForgeClient.registerClientPayloadHandlers(PACKET_CHANNEL);
        } else {
            registerLegacyNoopClientPayloadHandlers();
        }
        PACKET_CHANNEL.messageBuilder(ClientHandshakeC2SPayload.class, 7, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(ClientHandshakeC2SPayload::new)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        this.c2sPacketHandler.onClientHandshake(player, payload.clientHandshakeBuffer());
                    }
                })
                .add();
        PACKET_CHANNEL.messageBuilder(UpdateRequestC2SPayload.class, 8, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(UpdateRequestC2SPayload::new)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        this.c2sPacketHandler.onClientUpdateRequest(player, payload.clientUpdateRequestBuffer());
                    }
                })
                .add();
    }

    private static void registerLegacyNoopClientPayloadHandlers() {
        registerLegacyNoopClientPayload(WaypointListS2CPayload.class, 0, WaypointListS2CPayload::new);
        registerLegacyNoopClientPayload(DimensionWaypointS2CPayload.class, 1, DimensionWaypointS2CPayload::new);
        registerLegacyNoopClientPayload(WorldWaypointS2CPayload.class, 2, WorldWaypointS2CPayload::new);
        registerLegacyNoopClientPayload(WaypointModificationS2CPayload.class, 3, WaypointModificationS2CPayload::new);
        registerLegacyNoopClientPayload(UpdatesBundleS2CPayload.class, 4, UpdatesBundleS2CPayload::new);
        registerLegacyNoopClientPayload(ServerHandshakeS2CPayload.class, 5, ServerHandshakeS2CPayload::new);
        if (Features.noXaerosMod) {
            registerLegacyNoopClientPayload(XaerosWorldIdS2CPayload.class, 6, XaerosWorldIdS2CPayload::new);
        }
    }

    private static <T extends _959.server_waypoint.common.network.payload.ModPayload> void registerLegacyNoopClientPayload(
            Class<T> payloadClass,
            int id,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, T> decoder
    ) {
        PACKET_CHANNEL.messageBuilder(payloadClass, id, PlayNetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(decoder::apply)
                .consumerMainThread((payload, context) -> {})
                .add();
    }
^///?}

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
*///?}
