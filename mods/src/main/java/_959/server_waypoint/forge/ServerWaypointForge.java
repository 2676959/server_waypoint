package _959.server_waypoint.forge;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.WaypointEditRequestC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UploadChunkC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.ServerHandshakeS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.UpdatesBundleS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointEditResultS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListUpdateS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.XaerosWorldIdS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.UploadRequestS2CPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.forge.permission.ForgePermissionManager;
//? if >= 1.20.5
import io.netty.buffer.ByteBuf;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
//? if >= 1.20.5
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
//? if < 1.21.6
/*import net.minecraftforge.eventbus.api.IEventBus;*/
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//? if < 1.21.6
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;*/
import net.minecraftforge.network.NetworkDirection;
//? if <= 1.20.1 {
/*import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
*///?} else {
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
//?}

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;
import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

@Mod(ModInfo.MOD_ID)
public class ServerWaypointForge implements IPlatformConfigPath {
    private static final String NETWORK_PROTOCOL_VERSION = "5";
//? if <= 1.20.1 {
    /*public static final SimpleChannel PACKET_CHANNEL = NetworkRegistry.newSimpleChannel(
            modId("main"),
            () -> NETWORK_PROTOCOL_VERSION,
            NETWORK_PROTOCOL_VERSION::equals,
            NETWORK_PROTOCOL_VERSION::equals
    );
*///?} else {
    public static final SimpleChannel PACKET_CHANNEL = ChannelBuilder
            .named(modId("main"))
            .networkProtocolVersion(Integer.parseInt(NETWORK_PROTOCOL_VERSION))
            .simpleChannel();
    //?}

    private final WaypointServerMod waypointServer;
    private final C2SPacketHandler<CommandSourceStack, String, ServerPlayer> c2sPacketHandler;
    private final WaypointCommand waypointCommand;
    private final ModChatMessageHandler<String> chatMessageHandler;

    public ServerWaypointForge() {
        ModMessageSender messageSender = ModMessageSender.getInstance();
        ForgePermissionManager permissionManager = new ForgePermissionManager();
        this.chatMessageHandler = new ModChatMessageHandler<>(messageSender, permissionManager) {};
        this.waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), this.chatMessageHandler);
        UploadCoordinator<ServerPlayer> uploadCoordinator = new UploadCoordinator<>(
                this.waypointServer,
                messageSender::sendPlayerMessage,
                messageSender::broadcastPacket,
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.upload(), CONFIG.CommandPermission().upload()),
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.uploadDelete(), CONFIG.CommandPermission().uploadDelete())
        );
        this.c2sPacketHandler = new C2SPacketHandler<>(
                messageSender,
                this.waypointServer,
                permissionManager,
                this.waypointServer.navigation().service(),
                uploadCoordinator
        );
        this.waypointCommand = new WaypointCommand(this.waypointServer, messageSender, permissionManager, uploadCoordinator);

        //? if < 1.21.6
        /*IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();*/
        this.configureLoadedMods();
        this.registerPayloads();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            //? if < 1.21.6 {
            /*ServerWaypointForgeClient.initialize(modEventBus);
            *///?} else {
            ServerWaypointForgeClient.initialize();
            //?}
        }
        //? if < 1.21.6 {
        /*MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::listenChatMessages);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerRespawn);
        *///?} else {
        ServerStartingEvent.BUS.addListener(this::onServerStarting);
        ServerStoppingEvent.BUS.addListener(this::onServerStopping);
        ServerChatEvent.BUS.addListener(this::listenChatMessages);
        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        TickEvent.ServerTickEvent.Post.BUS.addListener(this::onServerTick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onPlayerLogout);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(this::onPlayerRespawn);
        //?}
    }

    private void configureLoadedMods() {
        if (ModList/*? if < 26 {*//*.get()*//*?}*/.isLoaded("xaerominimap") || ModList/*? if < 26 {*//*.get()*//*?}*/.isLoaded("xaeroworldmap")) {
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

    //? if < 1.20.6 {
    /*private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            this.waypointServer.navigation().tick();
        }
    }
    *///?} else {
    private void onServerTick(TickEvent.ServerTickEvent.Post event) {
        this.waypointServer.navigation().tick();
    }
    //?}

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            this.waypointServer.navigation().onPlayerJoin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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

    private void registerPayloads() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ServerWaypointForgeClient.registerClientPayloadHandlers();
        } else {
            registerS2C(WaypointListS2CPayload.class, 0, /*? if >= 1.20.5 {*/ WaypointListS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointListS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(DimensionWaypointS2CPayload.class, 1, /*? if >= 1.20.5 {*/ DimensionWaypointS2CPayload.PACKET_CODEC /*?} else {*/ /*DimensionWaypointS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(WorldWaypointS2CPayload.class, 2, /*? if >= 1.20.5 {*/ WorldWaypointS2CPayload.PACKET_CODEC /*?} else {*/ /*WorldWaypointS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(WaypointModificationS2CPayload.class, 3, /*? if >= 1.20.5 {*/ WaypointModificationS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointModificationS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(UpdatesBundleS2CPayload.class, 4, /*? if >= 1.20.5 {*/ UpdatesBundleS2CPayload.PACKET_CODEC /*?} else {*/ /*UpdatesBundleS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(ServerHandshakeS2CPayload.class, 5, /*? if >= 1.20.5 {*/ ServerHandshakeS2CPayload.PACKET_CODEC /*?} else {*/ /*ServerHandshakeS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(WaypointEditResultS2CPayload.class, 9, /*? if >= 1.20.5 {*/ WaypointEditResultS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointEditResultS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(WaypointListUpdateS2CPayload.class, 10, /*? if >= 1.20.5 {*/ WaypointListUpdateS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointListUpdateS2CPayload::new *//*?}*/, (payload, context) -> {});
            registerS2C(UploadRequestS2CPayload.class, 12, /*? if >= 1.20.5 {*/ UploadRequestS2CPayload.PACKET_CODEC /*?} else {*/ /*UploadRequestS2CPayload::new *//*?}*/, (payload, context) -> {});
        }
        if (Features.noXaerosMod) {
            registerS2C(XaerosWorldIdS2CPayload.class, 6, /*? if >= 1.20.5 {*/ XaerosWorldIdS2CPayload.PACKET_CODEC /*?} else {*/ /*XaerosWorldIdS2CPayload::new *//*?}*/, (payload, context) -> {});
        }
        registerC2S(ClientHandshakeC2SPayload.class, 7, /*? if >= 1.20.5 {*/ ClientHandshakeC2SPayload.PACKET_CODEC /*?} else {*/ /*ClientHandshakeC2SPayload::new *//*?}*/, (payload, context) -> {
//? if <= 1.20.1 {
            /*ServerPlayer player = context.get().getSender();
*///?} else {
            ServerPlayer player = context.getSender();
            //?}
            if (player != null) {
                this.c2sPacketHandler.onClientHandshake(player, payload.clientHandshakeBuffer());
            }
        });
        registerC2S(UpdateRequestC2SPayload.class, 8, /*? if >= 1.20.5 {*/ UpdateRequestC2SPayload.PACKET_CODEC /*?} else {*/ /*UpdateRequestC2SPayload::new *//*?}*/, (payload, context) -> {
//? if <= 1.20.1 {
            /*ServerPlayer player = context.get().getSender();
*///?} else {
            ServerPlayer player = context.getSender();
            //?}
            if (player != null) {
                this.c2sPacketHandler.onClientUpdateRequest(player, payload.clientUpdateRequestBuffer());
            }
        });
        registerC2S(WaypointEditRequestC2SPayload.class, 11, /*? if >= 1.20.5 {*/ WaypointEditRequestC2SPayload.PACKET_CODEC /*?} else {*/ /*WaypointEditRequestC2SPayload::new *//*?}*/, (payload, context) -> {
//? if <= 1.20.1 {
            /*ServerPlayer player = context.get().getSender();
*///?} else {
            ServerPlayer player = context.getSender();
            //?}
            if (player != null) {
                this.c2sPacketHandler.onWaypointEditRequest(player, payload.request());
            }
        });
        registerC2S(UploadChunkC2SPayload.class, 13, /*? if >= 1.20.5 {*/ UploadChunkC2SPayload.PACKET_CODEC /*?} else {*/ /*UploadChunkC2SPayload::new *//*?}*/, (payload, context) -> {
//? if <= 1.20.1 {
            /*ServerPlayer player = context.get().getSender();
*///?} else {
            ServerPlayer player = context.getSender();
            //?}
            if (player != null) {
                this.c2sPacketHandler.onUploadChunk(player, payload.uploadChunkBuffer());
            }
        });
    }

    private static <T extends ModPayload> void registerS2C(
            Class<T> payloadClass,
            int id,
            //? if >= 1.20.5 {
            StreamCodec<ByteBuf, T> codec,
            //?} else {
            /*Function<FriendlyByteBuf, T> decoder,
            *///?}
            //? if <= 1.20.1 {
            /*BiConsumer<T, Supplier<NetworkEvent.Context>> handler
            *///?} else {
            BiConsumer<T, CustomPayloadEvent.Context> handler
            //?}
    ) {
//? if <= 1.20.1 {
        /*PACKET_CHANNEL.registerMessage(
                id,
                payloadClass,
                (payload, buf) -> payload.write(buf),
                decoder,
                handler,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
*///?} else {
        PACKET_CHANNEL.messageBuilder(payloadClass, id, NetworkDirection.PLAY_TO_CLIENT)
                //? if >= 1.20.5 {
                .codec((StreamCodec) codec)
                //?} else {
                /*.encoder((payload, buf) -> payload.write(buf))
                .decoder(decoder)
                *///?}
                .consumerMainThread(handler)
                .add();
        //?}
    }

    private static <T extends ModPayload> void registerC2S(
            Class<T> payloadClass,
            int id,
            //? if >= 1.20.5 {
            StreamCodec<ByteBuf, T> codec,
            //?} else {
            /*Function<FriendlyByteBuf, T> decoder,
            *///?}
            //? if <= 1.20.1 {
            /*BiConsumer<T, Supplier<NetworkEvent.Context>> handler
            *///?} else {
            BiConsumer<T, CustomPayloadEvent.Context> handler
            //?}
    ) {
//? if <= 1.20.1 {
        /*PACKET_CHANNEL.registerMessage(
                id,
                payloadClass,
                (payload, buf) -> payload.write(buf),
                decoder,
                (payload, context) -> {
                    context.get().enqueueWork(() -> handler.accept(payload, context));
                    context.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
*///?} else {
        PACKET_CHANNEL.messageBuilder(payloadClass, id, NetworkDirection.PLAY_TO_SERVER)
                //? if >= 1.20.5 {
                .codec((StreamCodec) codec)
                //?} else {
                /*.encoder((payload, buf) -> payload.write(buf))
                .decoder(decoder)
                *///?}
                .consumerMainThread(handler)
                .add();
        //?}
    }

    private void registerCommands(RegisterCommandsEvent event) {
        this.waypointCommand.register(event.getDispatcher());
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(ModInfo.MOD_ID);
    }
}
