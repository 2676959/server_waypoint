//? if neoforge {
/*package _959.server_waypoint.neoforge;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.MessageChunkC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UploadChunkC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
//? if <= 1.20.4 {
/^import net.neoforged.neoforge.event.TickEvent;
^///?} else {
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}
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

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;
import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

@Mod(ModInfo.MOD_ID)
public class ServerWaypointNeoForge implements IPlatformConfigPath {
    private static final String NETWORK_PROTOCOL_VERSION = "9";
//? if = 1.20.2 {
    /^public static final SimpleChannel PACKET_CHANNEL = NetworkRegistry.newSimpleChannel(
            modId("main"),
            () -> NETWORK_PROTOCOL_VERSION,
            NETWORK_PROTOCOL_VERSION::equals,
            NETWORK_PROTOCOL_VERSION::equals
    );
    ^///?}

    private final WaypointServerMod waypointServer;
    private final C2SPacketHandler<CommandSourceStack, String, ServerPlayer> c2sPacketHandler;
    private final WaypointCommand waypointCommand;
    private final ModChatMessageHandler<String> chatMessageHandler;

    public ServerWaypointNeoForge(IEventBus modEventBus) {
        ModMessageSender messageSender = ModMessageSender.getInstance();
        NeoForgePermissionManager permissionManager = new NeoForgePermissionManager();
        this.chatMessageHandler = new ModChatMessageHandler<>(messageSender, permissionManager) {};
        this.waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), this.chatMessageHandler);
        UploadCoordinator<ServerPlayer> uploadCoordinator = new UploadCoordinator<>(
                this.waypointServer,
                messageSender::sendPlayerMessage,
                messageSender::broadcastChunkedMessage,
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.upload(), CONFIG.CommandPermission().upload()),
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.uploadDelete(), CONFIG.CommandPermission().uploadDelete()),
                this.waypointServer.navigation().service(),
                ServerPlayer::getUUID
        );
        this.c2sPacketHandler = new C2SPacketHandler<>(
                messageSender,
                this.waypointServer,
                permissionManager,
                this.waypointServer.navigation().service(),
                uploadCoordinator
        );
        this.waypointCommand = new WaypointCommand(this.waypointServer, messageSender, permissionManager, uploadCoordinator);

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
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn);
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
        this.c2sPacketHandler.resetSession();
        this.waypointServer.unload();
    }

//? if <= 1.20.4 {
    /^private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            this.waypointServer.navigation().tick();
            ModMessageSender.getInstance().tickChunkedMessages();
            this.c2sPacketHandler.tickUploadTransport();
        }
    }
    ^///?} else {
    private void onServerTick(ServerTickEvent.Post event) {
        this.waypointServer.navigation().tick();
        ModMessageSender.getInstance().tickChunkedMessages();
        this.c2sPacketHandler.tickUploadTransport();
    }
//?}

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModMessageSender.getInstance().disconnectChunkedMessages(player);
            this.waypointServer.navigation().onPlayerJoin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            this.c2sPacketHandler.onDisconnect(player);
            this.waypointServer.navigation().onPlayerQuit(player);
        }
    }

    private void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            this.waypointServer.navigation().onPlayerRespawn(player);
        }
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
            registrar.playToClient(MessageChunkS2CPayload.ID, MessageChunkS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC, (payload, context) -> {});
            registrar.playToClient(UploadRequestS2CPayload.ID, UploadRequestS2CPayload.PACKET_CODEC, (payload, context) -> {});
        }
        if (Features.noXaerosMod) {
            registrar.playToClient(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC, (payload, context) -> {});
        }
        // C2S
        registrar.playToServer(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC, (payload, context) ->
                context.enqueueWork(() -> this.c2sPacketHandler.onClientHandshake((ServerPlayer) context.player(), payload.clientHandshakeBuffer()))
        );
        registrar.playToServer(MessageChunkC2SPayload.ID, MessageChunkC2SPayload.PACKET_CODEC, (payload, context) ->
                context.enqueueWork(() -> this.c2sPacketHandler.onMessageChunk((ServerPlayer) context.player(), payload.messageChunk()))
        );
        registrar.playToServer(UploadChunkC2SPayload.ID, UploadChunkC2SPayload.PACKET_CODEC, (payload, context) ->
                context.enqueueWork(() -> this.c2sPacketHandler.onUploadChunk((ServerPlayer) context.player(), payload.uploadChunk()))
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
        registrar.play(MessageChunkC2SPayload.MESSAGE_CHUNK_PAYLOAD_ID, MessageChunkC2SPayload::new, handler ->
                handler.server((payload, context) -> context.workHandler().execute(() -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        this.c2sPacketHandler.onMessageChunk(player, payload.messageChunk());
                    }
                }))
        );
        registrar.play(UploadChunkC2SPayload.UPLOAD_CHUNK_PAYLOAD_ID, UploadChunkC2SPayload::new, handler ->
                handler.server((payload, context) -> context.workHandler().execute(() -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        this.c2sPacketHandler.onUploadChunk(player, payload.uploadChunk());
                    }
                }))
        );
    }

    private static void registerNoopClientPayloadHandlers(IPayloadRegistrar registrar) {
        registrar.play(MessageChunkS2CPayload.MESSAGE_CHUNK_PAYLOAD_ID, MessageChunkS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(ServerHandshakeS2CPayload.SERVER_HANDSHAKE_PAYLOAD, ServerHandshakeS2CPayload::new, handler -> handler.client((payload, context) -> {}));
        registrar.play(UploadRequestS2CPayload.UPLOAD_REQUEST_PAYLOAD_ID, UploadRequestS2CPayload::new, handler -> handler.client((payload, context) -> {}));
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
        PACKET_CHANNEL.messageBuilder(ClientHandshakeC2SPayload.class, 4, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(ClientHandshakeC2SPayload::new)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        this.c2sPacketHandler.onClientHandshake(player, payload.clientHandshakeBuffer());
                    }
                })
                .add();
        PACKET_CHANNEL.messageBuilder(MessageChunkC2SPayload.class, 5, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(MessageChunkC2SPayload::new)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        this.c2sPacketHandler.onMessageChunk(player, payload.messageChunk());
                    }
                })
                .add();
        PACKET_CHANNEL.messageBuilder(UploadChunkC2SPayload.class, 6, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(UploadChunkC2SPayload::new)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        this.c2sPacketHandler.onUploadChunk(player, payload.uploadChunk());
                    }
                })
                .add();
    }

    private static void registerLegacyNoopClientPayloadHandlers() {
        registerLegacyNoopClientPayload(MessageChunkS2CPayload.class, 0, MessageChunkS2CPayload::new);
        registerLegacyNoopClientPayload(ServerHandshakeS2CPayload.class, 1, ServerHandshakeS2CPayload::new);
        registerLegacyNoopClientPayload(UploadRequestS2CPayload.class, 2, UploadRequestS2CPayload::new);
        if (Features.noXaerosMod) {
            registerLegacyNoopClientPayload(XaerosWorldIdS2CPayload.class, 3, XaerosWorldIdS2CPayload::new);
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
