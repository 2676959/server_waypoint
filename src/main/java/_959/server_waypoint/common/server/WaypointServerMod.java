package _959.server_waypoint.common.server;

import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;

public class WaypointServerMod extends WaypointServerCore {
    public static MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public static WaypointServerMod INSTANCE;
    public final ModChatMessageHandler<String> chatMessageHandler;

    public WaypointServerMod(Path configDir, ModChatMessageHandler<String> handler) {
        super(configDir);
        INSTANCE = this;
        this.chatMessageHandler = handler;
    }

    @Override
    public boolean isDimensionKeyValid(String dimensionName) {
        if (MINECRAFT_SERVER == null) {
            LOGGER.warn("MinecraftServer is not initialized");
            return false;
        } else {
            RegistryKey<World> dimKey = getDimensionKey(dimensionName);
            World world = MINECRAFT_SERVER.getWorld(dimKey);
            return world != null;
        }
    }

    public @Nullable WorldWaypointS2CPayload toWorldWaypointPayload() {
        List<DimensionWaypointBuffer> dimensionWaypointBuffers = new ArrayList<>();

        for(WaypointFileManager fileManager : this.getFileManagerMap().values()) {
            if (fileManager != null) {
                dimensionWaypointBuffers.add(fileManager.toDimensionWaypoint());
            }
        }

        if (dimensionWaypointBuffers.isEmpty()) {
            return null;
        } else {
            return new WorldWaypointS2CPayload(new WorldWaypointBuffer(dimensionWaypointBuffers, EDITION));
        }
    }

    public void setMinecraftServer(MinecraftServer server) {
        if (MINECRAFT_SERVER == null) {
            MINECRAFT_SERVER = server;
            chatMessageHandler.setServer(server);
            if (CONFIG.Features().sendXaerosWorldId()) {
                this.initXearoWorldId(server.getSavePath(WorldSavePath.LEVEL_DAT).getParent());
            }
        }
    }
}
