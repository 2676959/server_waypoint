package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.listener.ChatMessageListenerPaperMC;
import _959.server_waypoint.listener.PlayerRegisterChannelListener;
import _959.server_waypoint.network.PaperChatMessageHandler;
import _959.server_waypoint.network.PaperMessageSender;
import _959.server_waypoint.server.WaypointServerPlugin;
import _959.server_waypoint.server.command.WaypointCommand;
import _959.server_waypoint.server.command.permission.PaperPermissionManager;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.network.MessageChannelID.*;
import static _959.server_waypoint.util.DimensionFileHelper.getFileName;
import static net.kyori.adventure.text.Component.text;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    public static final String XAEROWORLDMAP_CHANNEL = "xaeroworldmap:main";
    public static final String XAEROMINIMAP_CHANNEL = "xaerominimap:main";
    private WaypointServerPlugin waypointServer;
    private ChatMessageListenerPaperMC chatListener;
    private PlayerRegisterChannelListener channelRegisterListener;
    private WaypointCommand waypointCommand;
    private @SuppressWarnings("UnstableApiUsage") ClientHandshakeHandler<CommandSourceStack, Player> handshakeHandler;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        // Plugin startup logic
        if (waypointServer == null) {
            Server server = getServer();
            waypointServer = new WaypointServerPlugin(this.getAssignedConfigDirectory(), server.getWorldContainer().toPath());
            try {
                waypointServer.initServer();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            PaperMessageSender sender = new PaperMessageSender(this);
            PaperPermissionManager permissionManager = new PaperPermissionManager();
            waypointCommand = new WaypointCommand(sender, permissionManager);
            waypointCommand.enable();
            chatListener = new ChatMessageListenerPaperMC(new PaperChatMessageHandler(server, sender, permissionManager));
            channelRegisterListener = new PlayerRegisterChannelListener();
            this.handshakeHandler = new ClientHandshakeHandler<>(sender);
            this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
                commands.registrar().register(waypointCommand.build());
            });
            server.getPluginManager().registerEvents(chatListener, this);
            server.getPluginManager().registerEvents(channelRegisterListener, this);
            registerChannels();
        }
    }

    private void registerChannels() {
        // register for server_waypoint mod
        Server server = getServer();
        server.getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_LIST_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, DIMENSION_WAYPOINT_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, WORLD_WAYPOINT_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_MODIFICATION_CHANNEL.toString());
        server.getMessenger().registerIncomingPluginChannel(this, HANDSHAKE_CHANNEL.toString(), this);
        // register for xaero's minimap and world map mod
        server.getMessenger().registerOutgoingPluginChannel(this, XAEROWORLDMAP_CHANNEL);
        server.getMessenger().registerOutgoingPluginChannel(this, XAEROMINIMAP_CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (this.isEnabled() && channel.equals("server_waypoint:handshake")) {
            ByteArrayDataInput input = ByteStreams.newDataInput(message);
            int clientEdition = input.readInt();
            this.handshakeHandler.onHandshake(player, clientEdition);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        waypointCommand.disable();
        Server server = getServer();
        server.getMessenger().unregisterIncomingPluginChannel(this);
        server.getMessenger().unregisterOutgoingPluginChannel(this);
        AsyncChatEvent.getHandlerList().unregister(chatListener);
        PlayerRegisterChannelEvent.getHandlerList().unregister(channelRegisterListener);
        waypointServer.saveAllFiles();
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return getDataFolder().toPath();
    }

    public static JavaPlugin getSelf() {
        return getPlugin(ServerWaypointPaperMC.class);
    }
}
