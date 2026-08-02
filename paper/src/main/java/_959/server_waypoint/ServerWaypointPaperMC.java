package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.PayloadID;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
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
    private @SuppressWarnings("UnstableApiUsage") C2SPacketHandler<CommandSourceStack, Player> c2sPacketHandler;

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

        waypointCommand = new WaypointCommand(
                waypointServer,
                sender,
                permissionManager,
                this.navigationService
        );
        ChatMessageListenerPaperMC chatListener = new ChatMessageListenerPaperMC(
                this,
                new PaperChatMessageHandler(server, sender, permissionManager)
        );
        PlayerRegisterChannelListener channelRegisterListener = new PlayerRegisterChannelListener();
        NavigationProtectionListener navigationListener = new NavigationProtectionListener(
                this,
                waypointServer,
                this.navigationService,
                navigationPlatform,
                this.navigationItemManager,
                List.<PaperItemNavigationHandler>of(compassHandler, mapHandler)
        );
        this.c2sPacketHandler = new C2SPacketHandler<>(sender, waypointServer);
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
        waypointCommand = null;
        waypointServer = null;
    }

    private void registerChannels() {
        // register for server_waypoint mod
        Server server = getServer();
        Messenger messenger = server.getMessenger();
        messenger.registerOutgoingPluginChannel(this, WAYPOINT_LIST_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, DIMENSION_WAYPOINT_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, WORLD_WAYPOINT_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, WAYPOINT_MODIFICATION_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, UPDATES_BUNDLE_CHANNEL.ID);
        messenger.registerOutgoingPluginChannel(this, SERVER_HANDSHAKE_CHANNEL.ID);
        // register for incoming
        messenger.registerIncomingPluginChannel(this, CLIENT_HANDSHAKE_CHANNEL.ID, this);
        messenger.registerIncomingPluginChannel(this, CLIENT_UPDATE_REQUEST_CHANNEL.ID, this);

        // register for xaero's minimap mod
        messenger.registerOutgoingPluginChannel(this, XAEROS_WORLD_ID_CHANNEL.ID);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (this.isEnabled()) {
            switch (channel) {
                case ModInfo.MOD_ID + ":" + PayloadID.CLIENT_HANDSHAKE -> {
                    ByteBuf buf = Unpooled.copiedBuffer(message);
                    this.c2sPacketHandler.onClientHandshake(player, ClientHandshakeCodec.decode(buf));
                }
                case ModInfo.MOD_ID + ":" + PayloadID.CLIENT_UPDATE_REQUEST -> {
                    ByteBuf buf = Unpooled.copiedBuffer(message);
                    this.c2sPacketHandler.onClientUpdateRequest(player, ClientUpdateRequestBufferCodec.decode(buf));
                }
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
