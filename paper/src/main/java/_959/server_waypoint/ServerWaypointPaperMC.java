package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.ClientCommunicationHandler;
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
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static _959.server_waypoint.core.network.MessageChannelID.*;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    private WaypointServerPlugin waypointServer;
    private WaypointCommand waypointCommand;
    private @SuppressWarnings("UnstableApiUsage") ClientCommunicationHandler<CommandSourceStack, Player> handshakeHandler;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        // Plugin startup logic
        Server server = getServer();
        waypointServer = new WaypointServerPlugin(this.getAssignedConfigDirectory(), server.getWorldContainer().toPath());
        PaperMessageSender sender = new PaperMessageSender(this);
        PaperPermissionManager permissionManager = new PaperPermissionManager();
        waypointCommand = new WaypointCommand(sender, permissionManager);
        ChatMessageListenerPaperMC chatListener = new ChatMessageListenerPaperMC(new PaperChatMessageHandler(server, sender, permissionManager));
        PlayerRegisterChannelListener channelRegisterListener = new PlayerRegisterChannelListener();
        this.handshakeHandler = new ClientCommunicationHandler<>(sender, waypointServer);
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
            waypointCommand.enable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        waypointCommand.disable();
        waypointServer.freeAllLoadedFiles();
        waypointCommand = null;
        waypointServer = null;
    }

    private void registerChannels() {
        // register for server_waypoint mod
        Server server = getServer();
        server.getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_LIST_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, DIMENSION_WAYPOINT_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, WORLD_WAYPOINT_CHANNEL.toString());
        server.getMessenger().registerOutgoingPluginChannel(this, WAYPOINT_MODIFICATION_CHANNEL.toString());
        server.getMessenger().registerIncomingPluginChannel(this, HANDSHAKE_CHANNEL.toString(), this);
        // register for xaero's minimap mod
        server.getMessenger().registerOutgoingPluginChannel(this, XAEROS_WORLD_ID_CHANNEL.toString());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (this.isEnabled() && channel.equals("server_waypoint:handshake")) {
            ByteBuf buffer = Unpooled.copiedBuffer(message);
            this.handshakeHandler.onClientUpdateRequest(player, ClientUpdateRequestBufferCodec.decode(buffer));
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
