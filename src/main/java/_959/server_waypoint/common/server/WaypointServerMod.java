package _959.server_waypoint.common.server;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asIntegratedServer;

public class WaypointServerMod extends WaypointServerCore {
    // the default value is true because this is used by WaypointClient to identify the server
    public static boolean isDedicated = true;
    public static MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public static WaypointServerMod INSTANCE;
    public final ModChatMessageHandler<String> chatMessageHandler;
    private boolean loaded = false;

    public WaypointServerMod(Path configDir, ModChatMessageHandler<String> handler) {
        super(configDir);
        this.chatMessageHandler = handler;
        INSTANCE = this;
    }

    public static WaypointServerMod getInstance() {
        return INSTANCE;
    }

    @Override
    public void addWaypoint(String dimensionName, String listName, SimpleWaypoint waypoint, Consumer<@NotNull WaypointFileManager> successAction, Consumer<@NotNull SimpleWaypoint> duplicateAction) {
        super.addWaypoint(dimensionName, listName, waypoint, (fileManager) -> {
            successAction.accept(fileManager);
            if (!isDedicated) {
                LOGGER.info("do some updates in rendering");
            }
        }, duplicateAction);
    }

    @Override
    public void addWaypointList(String dimensionName, String listName, Consumer<WaypointFileManager> successAction, Runnable listExistsAction) {
        super.addWaypointList(dimensionName, listName, (fileManager) -> {
            successAction.accept(fileManager);
            if (!isDedicated) {
                LOGGER.info("do some updates in rendering");
            }
        }, listExistsAction);
    }

    @Override
    public void updateWaypointProperties(@NotNull SimpleWaypoint waypoint, String initials, WaypointPos waypointPos, int rgb, int yaw, boolean global, Runnable successAction, Runnable identicalAction) {
        super.updateWaypointProperties(waypoint, initials, waypointPos, rgb, yaw, global, () -> {
            successAction.run();
            if (!isDedicated) {
                LOGGER.info("do some updates in rendering");
            }
        }, identicalAction);
    }

    @Override
    public void removeWaypoint(WaypointList waypointList, SimpleWaypoint waypoint) {
        if (!isDedicated) {
            LOGGER.info("do some updates in rendering");
        }
        super.removeWaypoint(waypointList, waypoint);
    }

    public void load(MinecraftServer minecraftServer) {
        setMinecraftServer(minecraftServer);
        isDedicated = minecraftServer.isDedicated();
        if (CONFIG.Features().sendXaerosWorldId()) {
            this.initXearoWorldId(minecraftServer.getSavePath(WorldSavePath.LEVEL_DAT).getParent());
        }
        try {
            if (isDedicated) {
                if (this.loaded) {
                    return;
                }
                initConfigAndLanguageResource();
                initOrReadWaypointFiles();
            } else {
                if (loaded) {
                    changeWaypointFilesDir(asIntegratedServer(minecraftServer.getSavePath(WorldSavePath.ROOT)));
                } else {
                    initConfigAndLanguageResource();
                    this.waypointFilesDir = asIntegratedServer(minecraftServer.getSavePath(WorldSavePath.ROOT));
                    initOrReadWaypointFiles();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.loaded = true;
    }

    public void unload() {
        freeAllLoadedFiles();
        setMinecraftServer(null);
        this.loaded = false;
        isDedicated = true;
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

    public void setMinecraftServer(MinecraftServer server) {
        if (MINECRAFT_SERVER == null) {
            MINECRAFT_SERVER = server;
            chatMessageHandler.setServer(server);
        }
    }
}
