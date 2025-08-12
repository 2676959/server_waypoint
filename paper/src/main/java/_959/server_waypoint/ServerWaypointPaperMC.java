package _959.server_waypoint;

import _959.server_waypoint.core.IPlatformConfigPath;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.server.WaypointServerPlugin;
import _959.server_waypoint.server.command.WaypointCommand;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;

import static _959.server_waypoint.server.WaypointServerPlugin.LOGGER;

public class ServerWaypointPaperMC extends JavaPlugin implements PluginMessageListener, IPlatformConfigPath {
    public static final String worldmapChannel = "xaeroworldmap:main";
    public static final String minimapChannel = "xaerominimap:main";

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
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(WaypointCommand.build());
        });

        getServer().getPluginManager().registerEvents(new ServerWaypointListener(), this);
    }

    private void registerChannels() {
        // register for server_waypoint mod
        getServer().getMessenger().registerOutgoingPluginChannel(this, modificationChannel);
        getServer().getMessenger().registerIncomingPluginChannel(this, handShakeChannel, this);

        // register for xaero's minimap and world map mod
        getServer().getMessenger().registerOutgoingPluginChannel(this, worldmapChannel);
        getServer().getMessenger().registerOutgoingPluginChannel(this, minimapChannel);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        LOGGER.info("channel: {}", channel);
        if (channel.equals("server_waypoint:handshake")) {
            ByteArrayDataInput input = ByteStreams.newDataInput(message);
//            String subchannel = input.readUTF();
//            LOGGER.info(subchannel);
//            LOGGER.info("received handshake message");
            int version = input.readInt();
            LOGGER.info("client version: {}", version);
            ByteBuf out = Unpooled.buffer();
            SimpleWaypoint waypoint = new SimpleWaypoint("NETWORK_TEST", "T", 0,0,0,1,0, true);
            WaypointModificationBuffer modificationBuffer = new WaypointModificationBuffer(
                    "dim%0",
                    "a",
                    waypoint,
                    WaypointModificationType.ADD,
                    1
            );
            WaypointModificationBufferCodec.encode(out, modificationBuffer);
            out.capacity(out.writerIndex());
            player.sendPluginMessage(this, modificationChannel, out.array());
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
