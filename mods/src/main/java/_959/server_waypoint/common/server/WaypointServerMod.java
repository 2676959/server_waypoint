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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asIntegratedServer;

public class WaypointServerMod extends WaypointServerCore {
    // the default value is true because this is used by WaypointClient to identify the server
    private static boolean runsWithClient = false;
    private static WaypointServerMod INSTANCE;
    public static MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public final ModChatMessageHandler<String> chatMessageHandler;
    private boolean loaded = false;

    public WaypointServerMod(Path configDir, ModChatMessageHandler<String> handler) {
        super(configDir);
        this.chatMessageHandler = handler;
        INSTANCE = this;
    }

    public static boolean runsWithClient() {
        return runsWithClient;
    }

    public static WaypointServerMod getInstance() {
        return INSTANCE;
    }

    @Override
    public void addWaypoint(String dimensionName, String listName, SimpleWaypoint waypoint, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> successAction, Consumer<@NotNull SimpleWaypoint> duplicateAction) {
        super.addWaypoint(dimensionName, listName, waypoint, (fileManager, waypointList) -> {
            successAction.accept(fileManager, waypointList);
            if (runsWithClient) {
                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                    OptimizedWaypointRenderer.add(waypoint);
                }
                WaypointManagerScreen.refreshWaypointLists(dimensionName);
            }
        }, duplicateAction);
    }

    @Override
    public void removeWaypoint(@NotNull WaypointFileManager fileManager, WaypointList waypointList, SimpleWaypoint waypoint) {
        if (runsWithClient) {
            String dimensionName = fileManager.getDimensionName();
            if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                OptimizedWaypointRenderer.remove(waypoint);
            }
            WaypointManagerScreen.refreshWaypointLists(dimensionName);
        }
        super.removeWaypoint(fileManager, waypointList, waypoint);
    }

    @Override
    public void updateWaypointProperties(@NotNull WaypointFileManager fileManager, @NotNull WaypointList waypointList, @NotNull SimpleWaypoint waypoint, String newName, String initials, WaypointPos waypointPos, int rgb, int yaw, boolean global, Runnable successAction, Runnable nameUsedAction, Runnable identicalAction) {
        super.updateWaypointProperties(fileManager, waypointList, waypoint, newName, initials, waypointPos, rgb, yaw, global, () -> {
            successAction.run();
            if (runsWithClient && fileManager.getDimensionName().equals(WaypointClientMod.getCurrentDimensionName())) {
                OptimizedWaypointRenderer.updateWaypoint(waypoint);
            }
        }, nameUsedAction, identicalAction);
    }

    @Override
    public void addWaypointList(String dimensionName, String listName, Consumer<WaypointFileManager> successAction, Runnable listExistsAction) {
        super.addWaypointList(dimensionName, listName, (fileManager) -> {
            successAction.accept(fileManager);
            if (runsWithClient) {
                WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
            }
        }, listExistsAction);
    }

    @Override
    public void removeWaypointList(@NotNull WaypointFileManager fileManager, String listName, Consumer<WaypointFileManager> successAction, Runnable listNotFoundAction, Runnable nonEmptyListAction) {
        super.removeWaypointList(fileManager, listName, (fileManager1) -> {
            successAction.accept(fileManager1);
            if (runsWithClient) {
                WaypointManagerScreen.updateWaypointLists(fileManager1.getDimensionName(), fileManager1.getWaypointLists());
            }
        }, listNotFoundAction, nonEmptyListAction);

    }

    public void load(MinecraftServer minecraftServer) {
        setMinecraftServer(minecraftServer);
        runsWithClient = !minecraftServer.isDedicatedServer();
        if (CONFIG.Features().sendXaerosWorldId()) {
            this.initXearoWorldId(minecraftServer.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent());
        }
        try {
            if (!runsWithClient) {
                WaypointList.excludeClientOnlyFields = true;
                if (this.loaded) {
                    return;
                }
                initConfigAndLanguageResource();
                initOrReadWaypointFiles();
            } else {
                WaypointList.excludeClientOnlyFields = false;
                if (loaded) {
                    changeWaypointFilesDir(asIntegratedServer(minecraftServer.getWorldPath(LevelResource.ROOT)));
                } else {
                    initConfigAndLanguageResource();
                    this.waypointFilesDir = asIntegratedServer(minecraftServer.getWorldPath(LevelResource.ROOT));
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
        runsWithClient = false;
    }

    public void setMinecraftServer(MinecraftServer server) {
        if (MINECRAFT_SERVER == null) {
            MINECRAFT_SERVER = server;
            chatMessageHandler.setServer(server);
        }
    }
}
