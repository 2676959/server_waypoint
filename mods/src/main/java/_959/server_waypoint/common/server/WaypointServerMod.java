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

import static _959.server_waypoint.core.WaypointFilesManagerCore.*;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asIntegratedServer;

public class WaypointServerMod extends WaypointServerCore {
    // the default value is true because this is used by WaypointClient to identify the server
    private static volatile boolean runsWithClient = false;
    private static volatile WaypointServerMod INSTANCE;
    public static volatile MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public final ModChatMessageHandler<String> chatMessageHandler;
    private final ModNavigationRuntime navigation = new ModNavigationRuntime();
    private volatile boolean loaded = false;

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
    public AddWaypointResult addWaypoint(
            String dimensionName,
            String listName,
            SimpleWaypoint waypoint,
            Consumer<AddWaypointResult> resultAction
    ) {
        return super.addWaypoint(dimensionName, listName, waypoint, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == AddWaypointStatus.ADDED && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(
                            dimensionName,
                            result.fileManager(),
                            () -> {
                                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                                    OptimizedWaypointRenderer.add(result.waypoint());
                                }
                                updateWaypointManagerView(dimensionName, result.dimensionCreated());
                                syncWaypointModification(
                                        dimensionName,
                                        listName,
                                        WaypointModificationType.ADD,
                                        result.waypointSnapshot(),
                                        result.waypointSnapshot().name()
                                );
                            }
                    );
                }
            }
        });
    }

    @Override
    public RemoveWaypointResult removeWaypoint(
            String dimensionName,
            String listName,
            String waypointName,
            Consumer<RemoveWaypointResult> resultAction
    ) {
        return super.removeWaypoint(dimensionName, listName, waypointName, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == RemoveWaypointStatus.REMOVED && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(
                            dimensionName,
                            result.fileManager(),
                            () -> {
                                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                                    OptimizedWaypointRenderer.remove(result.waypoint());
                                }
                                WaypointManagerScreen.updateWaypointWidget(dimensionName);
                                syncWaypointModification(dimensionName, listName, WaypointModificationType.REMOVE, null, waypointName);
                            }
                    );
                }
            }
        });
    }

    @Override
    public UpdateWaypointResult updateWaypointProperties(
            String dimensionName,
            String listName,
            String oldName,
            String newName,
            String initials,
            WaypointPos waypointPos,
            int rgb,
            int yaw,
            boolean global,
            Consumer<UpdateWaypointResult> resultAction
    ) {
        return super.updateWaypointProperties(
                dimensionName,
                listName,
                oldName,
                newName,
                initials,
                waypointPos,
                rgb,
                yaw,
                global,
                result -> {
                    try {
                        resultAction.accept(result);
                    } finally {
                        if (result.status() == UpdateWaypointStatus.UPDATED && runsWithClient) {
                            this.runOnClientThreadIfGenerationActive(
                                    dimensionName,
                                    result.fileManager(),
                                    () -> {
                                        if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                                            OptimizedWaypointRenderer.updateWaypoint(result.waypoint());
                                        }
                                        WaypointManagerScreen.updateWaypointWidget(dimensionName);
                                        syncWaypointModification(
                                                dimensionName,
                                                listName,
                                                WaypointModificationType.UPDATE,
                                                result.afterSnapshot(),
                                                oldName
                                        );
                                    }
                            );
                        }
                    }
                }
        );
    }

    @Override
    public AddWaypointListResult addWaypointList(
            String dimensionName,
            String listName,
            Consumer<AddWaypointListResult> resultAction
    ) {
        return super.addWaypointList(dimensionName, listName, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == AddWaypointListStatus.ADDED && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(
                            dimensionName,
                            result.fileManager(),
                            () -> {
                                updateWaypointManagerView(dimensionName, result.dimensionCreated());
                                syncWaypointModification(dimensionName, listName, WaypointModificationType.ADD_LIST, null, null);
                            }
                    );
                }
            }
        });
    }

    @Override
    public RemoveWaypointListResult removeWaypointList(
            String dimensionName,
            String listName,
            Consumer<RemoveWaypointListResult> resultAction
    ) {
        return super.removeWaypointList(dimensionName, listName, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == RemoveWaypointListStatus.REMOVED && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(
                            dimensionName,
                            result.fileManager(),
                            () -> {
                                WaypointManagerScreen.updateWaypointWidget(dimensionName);
                                syncWaypointModification(dimensionName, listName, WaypointModificationType.REMOVE_LIST, null, null);
                            }
                    );
                }
            }
        });
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

    private void runOnClientThreadIfGenerationActive(
            String dimensionName,
            WaypointFileManager expectedManager,
            Runnable task
    ) {
        runOnClientThread(() -> this.readLifecycle(() -> {
            if (this.fileManagerMap.get(dimensionName) == expectedManager) {
                task.run();
            }
            return null;
        }));
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
