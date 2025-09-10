package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.ClientHandshakeHandler;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.network.PaperChatMessageHandler;
import _959.server_waypoint.network.PaperMessageSender;
import _959.server_waypoint.server.WaypointServerPlugin;
import _959.server_waypoint.server.command.WaypointCommand;
import _959.server_waypoint.server.command.permission.PaperPermissionManager;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;

import static _959.server_waypoint.core.network.MessageChannelID.*;
import static _959.server_waypoint.server.WaypointServerPlugin.LOGGER;
import static _959.server_waypoint.util.DimensionFileHelper.getFileName;
import static net.kyori.adventure.text.Component.text;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    public static final String worldmapChannel = "xaeroworldmap:main";
    public static final String minimapChannel = "xaerominimap:main";
    private @SuppressWarnings("UnstableApiUsage") ClientHandshakeHandler<CommandSourceStack, Player> handshakeHandler;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        // Plugin startup logic
        WaypointServerPlugin waypointServerPlugin = new WaypointServerPlugin(this.getAssignedConfigDirectory());
        try {
            waypointServerPlugin.initServer();
            waypointServerPlugin.initXearoWorldId(getServer().getWorldContainer().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PaperMessageSender sender = new PaperMessageSender(this);
        PaperPermissionManager permissionManager = new PaperPermissionManager();
        WaypointCommand waypointCommand = new WaypointCommand(sender, permissionManager);
        ServerWaypointListener listener = new ServerWaypointListener(new PaperChatMessageHandler(this.getServer(), sender, permissionManager));
        this.handshakeHandler = new ClientHandshakeHandler<>(sender);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(waypointCommand.build());
        });
        getServer().getPluginManager().registerEvents(listener, this);
        registerChannels();
    }

    private void registerChannels() {
        // register for server_waypoint mod
        getServer().getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_LIST_CHANNEL.toString());
        getServer().getMessenger().registerOutgoingPluginChannel(this, DIMENSION_WAYPOINT_CHANNEL.toString());
        getServer().getMessenger().registerOutgoingPluginChannel(this, WORLD_WAYPOINT_CHANNEL.toString());
        getServer().getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_MODIFICATION_CHANNEL.toString());

        getServer().getMessenger().registerIncomingPluginChannel(this, HANDSHAKE_CHANNEL.toString(), this);

        // register for xaero's minimap and world map mod
        getServer().getMessenger().registerOutgoingPluginChannel(this, worldmapChannel);
        getServer().getMessenger().registerOutgoingPluginChannel(this, minimapChannel);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (channel.equals("server_waypoint:handshake")) {
            ByteArrayDataInput input = ByteStreams.newDataInput(message);
            int clientEdition = input.readInt();
            this.handshakeHandler.onHandshake(player, clientEdition);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public Path getAssignedConfigDirectory() {
        return getDataFolder().toPath();
    }

    public static JavaPlugin getSelf() {
        return getPlugin(ServerWaypointPaperMC.class);
    }
}
