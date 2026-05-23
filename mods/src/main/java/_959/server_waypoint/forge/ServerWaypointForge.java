package _959.server_waypoint.forge;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.ServerHandshakeS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.UpdatesBundleS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.XaerosWorldIdS2CPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.server.command.WaypointCommand;
import _959.server_waypoint.config.Features;
import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.forge.permission.ForgePermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

@Mod(ModInfo.MOD_ID)
public class ServerWaypointForge implements IPlatformConfigPath {
    private static final String NETWORK_PROTOCOL_VERSION = "1";
    public static final SimpleChannel PACKET_CHANNEL = NetworkRegistry.newSimpleChannel(
            _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, "main"),
            () -> NETWORK_PROTOCOL_VERSION,
            NETWORK_PROTOCOL_VERSION::equals,
            NETWORK_PROTOCOL_VERSION::equals
    );

    private final WaypointServerMod waypointServer;
    private final C2SPacketHandler<CommandSourceStack, ServerPlayer> c2sPacketHandler;
    private final WaypointCommand waypointCommand;
    private final ModChatMessageHandler<String> chatMessageHandler;

    public ServerWaypointForge() {
        ModMessageSender messageSender = ModMessageSender.getInstance();
        ForgePermissionManager permissionManager = new ForgePermissionManager();
        this.chatMessageHandler = new ModChatMessageHandler<>(messageSender, permissionManager) {};
        this.waypointServer = new WaypointServerMod(this.getAssignedConfigDirectory(), this.chatMessageHandler);
        this.c2sPacketHandler = new C2SPacketHandler<>(messageSender, this.waypointServer);
        this.waypointCommand = new WaypointCommand(this.waypointServer, messageSender, permissionManager);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        this.configureLoadedMods();
        this.registerPayloads();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ServerWaypointForgeClient.initialize(modEventBus);
        }
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::listenChatMessages);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
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

    private void registerPayloads() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ServerWaypointForgeClient.registerClientPayloadHandlers();
        } else {
            registerS2C(WaypointListS2CPayload.class, 0, WaypointListS2CPayload::new, (payload, context) -> {});
            registerS2C(DimensionWaypointS2CPayload.class, 1, DimensionWaypointS2CPayload::new, (payload, context) -> {});
            registerS2C(WorldWaypointS2CPayload.class, 2, WorldWaypointS2CPayload::new, (payload, context) -> {});
            registerS2C(WaypointModificationS2CPayload.class, 3, WaypointModificationS2CPayload::new, (payload, context) -> {});
            registerS2C(UpdatesBundleS2CPayload.class, 4, UpdatesBundleS2CPayload::new, (payload, context) -> {});
            registerS2C(ServerHandshakeS2CPayload.class, 5, ServerHandshakeS2CPayload::new, (payload, context) -> {});
        }
        if (Features.noXaerosMod) {
            registerS2C(XaerosWorldIdS2CPayload.class, 6, XaerosWorldIdS2CPayload::new, (payload, context) -> {});
        }
        registerC2S(ClientHandshakeC2SPayload.class, 7, ClientHandshakeC2SPayload::new, (payload, context) -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                this.c2sPacketHandler.onClientHandshake(player, payload.clientHandshakeBuffer());
            }
        });
        registerC2S(UpdateRequestC2SPayload.class, 8, UpdateRequestC2SPayload::new, (payload, context) -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                this.c2sPacketHandler.onClientUpdateRequest(player, payload.clientUpdateRequestBuffer());
            }
        });
    }

    private static <T extends ModPayload> void registerS2C(
            Class<T> payloadClass,
            int id,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        PACKET_CHANNEL.registerMessage(
                id,
                payloadClass,
                (payload, buf) -> payload.write(buf),
                decoder,
                handler,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static <T extends ModPayload> void registerC2S(
            Class<T> payloadClass,
            int id,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        PACKET_CHANNEL.registerMessage(
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
    }

    private void registerCommands(RegisterCommandsEvent event) {
        this.waypointCommand.register(event.getDispatcher());
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(ModInfo.MOD_ID);
    }
}
