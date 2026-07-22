package _959.server_waypoint.common.server;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.integrations.ClientWaypointSyncEvent;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.ModChatMessageHandler;
import _959.server_waypoint.common.server.navigation.ModNavigationRuntime;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.ThreadDispatching;
import net.minecraft.client.Minecraft;
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
    private final ModNavigationRuntime navigation = new ModNavigationRuntime();
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

    public ModNavigationRuntime navigation() {
        return this.navigation;
    }

    @Override
    public void addWaypoint(String dimensionName, String listName, SimpleWaypoint waypoint, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> successAction, Consumer<@NotNull SimpleWaypoint> duplicateAction) {
        boolean dimensionListChanged = this.getWaypointFileManager(dimensionName) == null;
        super.addWaypoint(dimensionName, listName, waypoint, (fileManager, waypointList) -> {
            successAction.accept(fileManager, waypointList);
            if (runsWithClient) {
                runOnClientThread(() -> {
                    if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                        OptimizedWaypointRenderer.add(waypoint);
                    }
                    updateWaypointManagerView(dimensionName, dimensionListChanged);
                    syncWaypointModification(dimensionName, listName, WaypointModificationType.ADD, waypoint, waypoint.name());
                });
            }
        }, duplicateAction);
    }

    @Override
    public void removeWaypoint(@NotNull WaypointFileManager fileManager, WaypointList waypointList, SimpleWaypoint waypoint) {
        super.removeWaypoint(fileManager, waypointList, waypoint);
        if (runsWithClient) {
            String dimensionName = fileManager.getDimensionName();
            runOnClientThread(() -> {
                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                    OptimizedWaypointRenderer.remove(waypoint);
                }
                WaypointManagerScreen.updateWaypointWidget(dimensionName);
                syncWaypointModification(dimensionName, waypointList.name(), WaypointModificationType.REMOVE, null, waypoint.name());
            });
        }
    }

    @Override
    public void updateWaypointProperties(@NotNull WaypointFileManager fileManager, @NotNull WaypointList waypointList, @NotNull SimpleWaypoint waypoint, String newName, String initials, WaypointPos waypointPos, int rgb, int yaw, boolean global, Runnable successAction, Runnable nameUsedAction, Runnable identicalAction) {
        String oldName = waypoint.name();
        super.updateWaypointProperties(fileManager, waypointList, waypoint, newName, initials, waypointPos, rgb, yaw, global, () -> {
            successAction.run();
            if (runsWithClient) {
                runOnClientThread(() -> {
                    if (fileManager.getDimensionName().equals(WaypointClientMod.getCurrentDimensionName())) {
                        OptimizedWaypointRenderer.updateWaypoint(waypoint);
                    }
                    WaypointManagerScreen.updateWaypointWidget(fileManager.getDimensionName());
                    syncWaypointModification(fileManager.getDimensionName(), waypointList.name(), WaypointModificationType.UPDATE, waypoint, oldName);
                });
            }
        }, nameUsedAction, identicalAction);
    }

    @Override
    public void addWaypointList(String dimensionName, String listName, Consumer<WaypointFileManager> successAction, Runnable listExistsAction) {
        boolean dimensionListChanged = this.getWaypointFileManager(dimensionName) == null;
        super.addWaypointList(dimensionName, listName, (fileManager) -> {
            successAction.accept(fileManager);
            if (runsWithClient) {
                runOnClientThread(() -> {
                    updateWaypointManagerView(dimensionName, dimensionListChanged);
                    syncWaypointModification(dimensionName, listName, WaypointModificationType.ADD_LIST, null, null);
                });
            }
        }, listExistsAction);
    }

    @Override
    public void removeWaypointList(@NotNull WaypointFileManager fileManager, String listName, Consumer<WaypointFileManager> successAction, Runnable listNotFoundAction, Runnable nonEmptyListAction) {
        super.removeWaypointList(fileManager, listName, (fileManager1) -> {
            successAction.accept(fileManager1);
            if (runsWithClient) {
                runOnClientThread(() -> {
                    WaypointManagerScreen.updateWaypointWidget(fileManager1.getDimensionName());
                    syncWaypointModification(fileManager1.getDimensionName(), listName, WaypointModificationType.REMOVE_LIST, null, null);
                });
            }
        }, listNotFoundAction, nonEmptyListAction);

    }

    /**
     * Routes an integrated-server mutation to the smallest manager refresh that can represent it.
     *
     * @param dimensionName the dimension whose data changed
     * @param dimensionListChanged whether the mutation created a new dimension manager
     */
    private static void updateWaypointManagerView(String dimensionName, boolean dimensionListChanged) {
        if (dimensionListChanged) {
            WaypointManagerScreen.updateWidgetsForDimensionListChange(dimensionName);
        } else {
            WaypointManagerScreen.updateWaypointWidget(dimensionName);
        }
    }

    private static void runOnClientThread(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        ThreadDispatching.runOnTargetThread(minecraft::isSameThread, minecraft::execute, task);
    }

    private static void syncWaypointModification(String dimensionName, String listName, WaypointModificationType type, SimpleWaypoint waypoint, String waypointName) {
        syncMapModIntegrations(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, type, waypoint, waypointName));
    }

    private static void syncMapModIntegrations(@NotNull ClientWaypointSyncEvent event) {
        MapModIntegrations.onClientWaypointSync(event, WaypointClientMod.getInstance());
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
        this.navigation.shutdown();
        freeAllLoadedFiles();
        setMinecraftServer(null);
        this.loaded = false;
        runsWithClient = false;
    }

    public void setMinecraftServer(MinecraftServer server) {
        MINECRAFT_SERVER = server;
        chatMessageHandler.setServer(server);
    }
}
