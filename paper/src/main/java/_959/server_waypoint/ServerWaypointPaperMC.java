package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.C2SPacketHandler;
import _959.server_waypoint.core.network.PayloadID;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import _959.server_waypoint.core.network.codec.ClientUpdateRequestBufferCodec;
import _959.server_waypoint.listener.ChatMessageListenerPaperMC;
import _959.server_waypoint.listener.PlayerRegisterChannelListener;
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

import static _959.server_waypoint.core.network.MessageChannelID.*;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    private WaypointServerPlugin waypointServer;
    private WaypointCommand waypointCommand;
    private @SuppressWarnings("UnstableApiUsage") C2SPacketHandler<CommandSourceStack, Player> c2sPacketHandler;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        // Plugin startup logic
        // You can find the plugin id of your plugins on
        // the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 29431;
        Metrics metrics = new Metrics(this, pluginId);
        // Add custom charts
        metrics.addCustomChart(new SingleLineChart("players", () -> Bukkit.getOnlinePlayers().size()));

        Server server = getServer();
        waypointServer = new WaypointServerPlugin(this.getAssignedConfigDirectory(), server.getWorldContainer().toPath());
        PaperMessageSender sender = new PaperMessageSender(this);
        PaperPermissionManager permissionManager = new PaperPermissionManager();
        waypointCommand = new WaypointCommand(waypointServer, sender, permissionManager);
        ChatMessageListenerPaperMC chatListener = new ChatMessageListenerPaperMC(new PaperChatMessageHandler(server, sender, permissionManager));
        PlayerRegisterChannelListener channelRegisterListener = new PlayerRegisterChannelListener();
        this.c2sPacketHandler = new C2SPacketHandler<>(sender, waypointServer);
        LiteralCommandNode<CommandSourceStack> command = waypointCommand.build();
        // register
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
                commands.registrar().register(command)
        );
        registerChannels();
        server.getPluginManager().registerEvents(chatListener, this);
        server.getPluginManager().registerEvents(channelRegisterListener, this);
        try {
            waypointServer.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        waypointServer.freeAllLoadedFiles();
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
}
