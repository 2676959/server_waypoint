package _959.server_waypoint.common.server;

import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import _959.server_waypoint.core.waypoint.WaypointModificationType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.waypointInfoText;

public class WaypointServerMod extends WaypointServerCore {
    public static MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public static WaypointServerMod INSTANCE;

    public WaypointServerMod(Path configDir) {
        super(configDir);
        INSTANCE = this;
    }

    @Override
    public boolean isDimensionKeyValid(String dimString) {
        if (MINECRAFT_SERVER == null) {
            LOGGER.warn("MinecraftServer is not initialized");
            return false;
        } else {
            RegistryKey<World> dimKey = getDimensionKey(dimString);
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
        }
    }
}
