package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.PayloadID;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
import _959.server_waypoint.core.network.codec.UploadChunkCodec;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.listener.ChatMessageListenerPaperMC;
import _959.server_waypoint.listener.NavigationProtectionListener;
import _959.server_waypoint.listener.PlayerRegisterChannelListener;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.PaperActionbarNavigationHandler;
import _959.server_waypoint.navigation.PaperBossbarNavigationHandler;
import _959.server_waypoint.navigation.PaperCompassNavigationHandler;
import _959.server_waypoint.navigation.PaperItemNavigationHandler;
import _959.server_waypoint.navigation.PaperMapNavigationHandler;
import _959.server_waypoint.navigation.PaperNavigationItemManager;
import _959.server_waypoint.navigation.PaperNavigationMapCache;
import _959.server_waypoint.navigation.PaperNavigationPlatform;
import _959.server_waypoint.navigation.PaperTextDisplayNavigationHandler;
import _959.server_waypoint.network.PaperChatMessageHandler;
import _959.server_waypoint.network.PaperMessageSender;
import _959.server_waypoint.server.WaypointServerPlugin;
import _959.server_waypoint.server.command.WaypointCommand;
import _959.server_waypoint.server.command.permission.PaperPermissionManager;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.core.network.MessageChannelID.*;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    private static final String BUILD_PROPERTIES_RESOURCE = "/server-waypoint-paper.properties";
    private static final String MINECRAFT_VERSION_RANGE_PROPERTY = "minecraft-version-range";

    private WaypointServerPlugin waypointServer;
    private WaypointCommand waypointCommand;
    private NavigationService<Player> navigationService;
    private PaperNavigationItemManager navigationItemManager;
    private PaperNavigationMapCache navigationMapCache;
    private PaperMessageSender messageSender;
    private @SuppressWarnings("UnstableApiUsage") C2SPacketHandler<CommandSourceStack, String, Player> c2sPacketHandler;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        enforceSupportedMinecraftVersion();

        // Plugin startup logic
        // You can find the plugin id of your plugins on
        // the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 29431;
        Metrics metrics = new Metrics(this, pluginId);
        // Add custom charts
        metrics.addCustomChart(new SingleLineChart("players", () -> Bukkit.getOnlinePlayers().size()));

        Server server = getServer();
        waypointServer = new WaypointServerPlugin(this.getAssignedConfigDirectory(), server.getWorldContainer().toPath());
        try {
            waypointServer.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        PaperMessageSender sender = new PaperMessageSender(this);
        this.messageSender = sender;
        PaperPermissionManager permissionManager = new PaperPermissionManager();
        this.navigationItemManager = new PaperNavigationItemManager();
        this.navigationMapCache = new PaperNavigationMapCache(this);
        PaperNavigationPlatform navigationPlatform = new PaperNavigationPlatform(
                this,
                this.navigationItemManager
        );
        PaperCompassNavigationHandler compassHandler = new PaperCompassNavigationHandler(
                navigationPlatform,
                this.navigationItemManager
        );
        PaperMapNavigationHandler mapHandler = new PaperMapNavigationHandler(
                this.navigationItemManager,
                this.navigationMapCache
        );
        PaperBossbarNavigationHandler bossbarHandler = new PaperBossbarNavigationHandler();
        PaperActionbarNavigationHandler actionbarHandler = new PaperActionbarNavigationHandler();
        PaperTextDisplayNavigationHandler textDisplayHandler =
                new PaperTextDisplayNavigationHandler();
        List<NavigationMethodHandler<Player>> navigationHandlers = List.of(
                compassHandler,
                mapHandler,
                bossbarHandler,
                actionbarHandler,
                textDisplayHandler
        );
        this.navigationService = new NavigationService<>(navigationPlatform, navigationHandlers);
        UploadCoordinator<Player> uploadCoordinator = new UploadCoordinator<>(
                waypointServer,
                sender::sendPlayerMessage,
                sender::broadcastChunkedMessage,
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.upload(), CONFIG.CommandPermission().upload()),
                player -> permissionManager.checkPlayerPermission(player, permissionManager.keys.uploadDelete(), CONFIG.CommandPermission().uploadDelete()),
                this.navigationService,
                Player::getUniqueId
        );

        waypointCommand = new WaypointCommand(
                waypointServer,
                sender,
                permissionManager,
                this.navigationService,
                uploadCoordinator
        );
        ChatMessageListenerPaperMC chatListener = new ChatMessageListenerPaperMC(
                this,
                new PaperChatMessageHandler(server, sender, permissionManager)
        );
        PlayerRegisterChannelListener channelRegisterListener = new PlayerRegisterChannelListener();
        this.c2sPacketHandler = new C2SPacketHandler<>(
                sender,
                waypointServer,
                permissionManager,
                this.navigationService,
                uploadCoordinator
        );
        NavigationProtectionListener navigationListener = new NavigationProtectionListener(
                this,
                waypointServer,
                this.navigationService,
                navigationPlatform,
                this.navigationItemManager,
                List.<PaperItemNavigationHandler>of(compassHandler, mapHandler),
                sender::disconnectChunkedMessages,
                this.c2sPacketHandler::onDisconnect
        );
        server.getAsyncScheduler().runAtFixedRate(
                this,
                ignored -> this.c2sPacketHandler.tickUploadTransport(),
                50L,
                50L,
                TimeUnit.MILLISECONDS
        );
        LiteralCommandNode<CommandSourceStack> command = waypointCommand.build();
        // register
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
                commands.registrar().register(command)
        );
        registerChannels();
        server.getPluginManager().registerEvents(chatListener, this);
        server.getPluginManager().registerEvents(channelRegisterListener, this);
        server.getPluginManager().registerEvents(navigationListener, this);
    }

    @Override
    public void onDisable() {
        if (this.c2sPacketHandler != null) {
            this.c2sPacketHandler.resetSession();
        }
        if (this.messageSender != null) {
            this.getServer().getOnlinePlayers()
                    .forEach(this.messageSender::disconnectChunkedMessages);
        }
        if (this.navigationService != null) {
            this.navigationService.shutdown();
        }
        if (this.navigationMapCache != null) {
            this.navigationMapCache.close();
        }
        if (waypointServer != null) {
            waypointServer.freeAllLoadedFiles();
        }
        this.navigationMapCache = null;
        this.navigationItemManager = null;
        this.navigationService = null;
        this.messageSender = null;
        waypointCommand = null;
        waypointServer = null;
    }

    private void registerChannels() {
        // register for server_waypoint mod
        Server server = getServer();
        Messenger messenger = server.getMessenger();
        messenger.registerOutgoingPluginChannel(this, MESSAGE_CHUNK_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, SERVER_HANDSHAKE_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, UPLOAD_REQUEST_CHANNEL.ID);
        // register for incoming
        messenger.registerIncomingPluginChannel(this, CLIENT_HANDSHAKE_CHANNEL.ID, this);
        messenger.registerIncomingPluginChannel(this, MESSAGE_CHUNK_CHANNEL.ID, this);
        messenger.registerIncomingPluginChannel(this, UPLOAD_CHUNK_CHANNEL.ID, this);

        // register for xaero's minimap mod
        messenger.registerOutgoingPluginChannel(this, XAEROS_WORLD_ID_CHANNEL.ID);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (this.isEnabled()) {
            ByteBuf buf = Unpooled.wrappedBuffer(message);
            try {
                switch (channel) {
                    case ModInfo.MOD_ID + ":" + PayloadID.CLIENT_HANDSHAKE ->
                            this.c2sPacketHandler.onClientHandshake(
                                    player,
                                    ClientHandshakeCodec.decode(buf)
                            );
                    case ModInfo.MOD_ID + ":" + PayloadID.MESSAGE_CHUNK ->
                            this.c2sPacketHandler.onMessageChunk(
                                    player,
                                    MessageChunkCodec.decode(buf)
                            );
                    case ModInfo.MOD_ID + ":" + PayloadID.UPLOAD_CHUNK ->
                            this.c2sPacketHandler.onUploadChunk(
                                    player,
                                    UploadChunkCodec.decode(buf)
                            );
                }
                if (buf.isReadable()) {
                    throw new IllegalArgumentException("Plugin message has trailing bytes");
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Rejected malformed plugin message on channel {}", channel, exception);
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return getDataFolder().toPath();
    }

    public static JavaPlugin getSelf() {
        return getPlugin(ServerWaypointPaperMC.class);
    }

    private static void enforceSupportedMinecraftVersion() {
        String minecraft = Bukkit.getMinecraftVersion();
        if (!CompatibilityChecker.isCompatible(minecraft)) {
            LOGGER.error("This build is only compatible with Minecraft: {}", CompatibilityChecker.getSupportedVersions());
            throw new IllegalStateException("Server waypoint plugin is incompatible with Minecraft %s".formatted(minecraft));
        };
    }
}
