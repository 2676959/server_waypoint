package _959.server_waypoint.common.server;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;
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
    private static boolean hasClient = false;
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

    public static boolean hasClient() {
        return hasClient;
    }

    public static WaypointServerMod getInstance() {
        return INSTANCE;
    }

    @Override
    public void addWaypoint(String dimensionName, String listName, SimpleWaypoint waypoint, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> successAction, Consumer<@NotNull SimpleWaypoint> duplicateAction) {
        super.addWaypoint(dimensionName, listName, waypoint, (fileManager, waypointList) -> {
            successAction.accept(fileManager, waypointList);
            if (hasClient) {
                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                    OptimizedWaypointRenderer.add(waypoint);
                    WaypointManagerScreen.refreshWaypointLists();
                }
            }
        }, duplicateAction);
    }

    @Override
    public void removeWaypoint(@NotNull WaypointFileManager fileManager, WaypointList waypointList, SimpleWaypoint waypoint) {
        if (hasClient) {
            if (fileManager.getDimensionName().equals(WaypointClientMod.getCurrentDimensionName())) {
                OptimizedWaypointRenderer.remove(waypoint);
                WaypointManagerScreen.refreshWaypointLists();
            }
        }
        super.removeWaypoint(fileManager, waypointList, waypoint);
    }

    @Override
    public void updateWaypointProperties(@NotNull WaypointFileManager fileManager, @NotNull SimpleWaypoint waypoint, String name, String initials, WaypointPos waypointPos, int rgb, int yaw, boolean global, Runnable successAction, Runnable identicalAction) {
        super.updateWaypointProperties(fileManager, waypoint, name, initials, waypointPos, rgb, yaw, global, () -> {
            successAction.run();
            if (hasClient) {
                if (fileManager.getDimensionName().equals(WaypointClientMod.getCurrentDimensionName())) {
                    OptimizedWaypointRenderer.updateWaypoint(waypoint);
                }
            }
        }, identicalAction);
    }

    @Override
    public void addWaypointList(String dimensionName, String listName, Consumer<WaypointFileManager> successAction, Runnable listExistsAction) {
        super.addWaypointList(dimensionName, listName, (fileManager) -> {
            successAction.accept(fileManager);
            if (hasClient) {
                WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
            }
        }, listExistsAction);
    }

    @Override
    public void removeWaypointList(@NotNull WaypointFileManager fileManager, String listName, Consumer<WaypointFileManager> successAction, Runnable listNotFoundAction, Runnable nonEmptyListAction) {
        super.removeWaypointList(fileManager, listName, (fileManager1) -> {
            successAction.accept(fileManager1);
            if (hasClient) {
                WaypointManagerScreen.updateWaypointLists(fileManager1.getDimensionName(), fileManager1.getWaypointLists());
            }
        }, listNotFoundAction, nonEmptyListAction);

    }

    public void load(MinecraftServer minecraftServer) {
        setMinecraftServer(minecraftServer);
        hasClient = !minecraftServer.isDedicated();
        if (CONFIG.Features().sendXaerosWorldId()) {
            this.initXearoWorldId(minecraftServer.getSavePath(WorldSavePath.LEVEL_DAT).getParent());
        }
        try {
            if (!hasClient) {
                WaypointList.excludeClientOnlyFields = true;
                if (this.loaded) {
                    return;
                }
                initConfigAndLanguageResource();
                initOrReadWaypointFiles();
            } else {
                WaypointList.excludeClientOnlyFields = false;
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
        hasClient = false;
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
