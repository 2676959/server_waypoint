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
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.WaypointEditResult;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.edit.WaypointListEditResult;
import _959.server_waypoint.core.edit.WaypointListPatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.ThreadDispatching;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    private final Path crossServerDirectory;
    private _959.server_waypoint.common.server.handoff.ModCrossServerRuntime crossServer;
    public void configureCrossServer(_959.server_waypoint.common.server.command.WaypointCommand command,
            _959.server_waypoint.command.permission.PermissionManager<net.minecraft.commands.CommandSourceStack, String, net.minecraft.server.level.ServerPlayer> permissions) {
        crossServer = new _959.server_waypoint.common.server.handoff.ModCrossServerRuntime(crossServerDirectory, this, command, permissions);
    }
    public void crossServerArrival(net.minecraft.server.level.ServerPlayer player) { if (crossServer != null) crossServer.arrived(player); }

    public WaypointServerMod(Path configDir, ModChatMessageHandler<String> handler) {
        super(configDir);
        this.crossServerDirectory = configDir;
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
        return this.addWaypoint(dimensionName, listName, listName, waypoint, resultAction);
    }

    @Override
    public AddWaypointResult addWaypoint(
            String dimensionName,
            String listName,
            String listDisplayName,
            SimpleWaypoint waypoint,
            Consumer<AddWaypointResult> resultAction
    ) {
        return super.addWaypoint(dimensionName, listName, listDisplayName, waypoint, result -> {
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
    public RestoreWaypointResult restoreWaypoint(
            String dimensionName,
            String listIdentifier,
            SimpleWaypoint waypoint,
            Consumer<RestoreWaypointResult> resultAction
    ) {
        return super.restoreWaypoint(dimensionName, listIdentifier, waypoint, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == RestoreWaypointStatus.RESTORED && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(dimensionName, result.fileManager(), () -> {
                        WaypointList list = result.fileManager().getWaypointListByName(listIdentifier);
                        SimpleWaypoint restored = list == null
                                ? null
                                : list.getWaypointByName(result.waypointSnapshot().name());
                        if (restored != null && list.isShow()
                                && dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                            OptimizedWaypointRenderer.add(restored);
                        }
                        WaypointManagerScreen.updateWaypointWidget(dimensionName);
                        syncWaypointModification(
                                dimensionName, listIdentifier, WaypointModificationType.ADD,
                                result.waypointSnapshot(), result.waypointSnapshot().name()
                        );
                    });
                }
            }
        });
    }

    @Override
    public WaypointListEditResult updateWaypointList(
            EditTarget target,
            @Nullable Integer expectedSyncNum,
            WaypointListPatch patch,
            Consumer<WaypointListEditResult> preCommitAction,
            Consumer<WaypointListEditResult> resultAction
    ) {
        return super.updateWaypointList(target, expectedSyncNum, patch, preCommitAction, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == EditResultStatus.SUCCESS && runsWithClient) {
                    this.runOnClientThreadIfGenerationActive(target.dimensionName(), result.fileManager(), () -> {
                        WaypointManagerScreen.updateWaypointWidget(target.dimensionName());
                        // Replace the dimension so a rename also removes the old map-mod list identity.
                        syncMapModIntegrations(ClientWaypointSyncEvent.dimensionReplaced(
                                target.dimensionName(), result.fileManager().getWaypointLists()
                        ));
                    });
                }
            }
        });
    }

    @Override
    public <T> RevisionedDimensionMutationResult<T> applyDimensionMutationIfRevision(
            String dimensionName,
            DimensionRevision expectedRevision,
            Function<WaypointFileManager.AtomicMutation, T> action
    ) {
        if (!runsWithClient) {
            return super.applyDimensionMutationIfRevision(dimensionName, expectedRevision, action);
        }
        AtomicReference<WaypointFileManager> mutatedManager = new AtomicReference<>();
        RevisionedDimensionMutationResult<T> result = super.applyDimensionMutationIfRevision(
                dimensionName, expectedRevision, mutation -> {
                    T value = action.apply(mutation);
                    // Capture the manager while the mutation still owns its world generation.
                    mutatedManager.set(this.getWaypointFileManager(dimensionName));
                    return value;
                }
        );
        if (result.status() == RevisionedDimensionMutationStatus.APPLIED && result.changed()) {
            WaypointFileManager manager = mutatedManager.get();
            this.runOnClientThreadIfGenerationActive(dimensionName, manager, () -> {
                List<WaypointList> lists = manager.getWaypointLists();
                if (dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                    OptimizedWaypointRenderer.clearScene();
                    OptimizedWaypointRenderer.loadScene(lists);
                }
                updateWaypointManagerView(dimensionName, !expectedRevision.exists());
                syncMapModIntegrations(ClientWaypointSyncEvent.dimensionReplaced(dimensionName, lists));
            });
        }
        return result;
    }

    @Override
    public WaypointEditResult updateWaypoint(
            EditTarget target,
            @Nullable Integer expectedSyncNum,
            WaypointPatch patch,
            Consumer<WaypointEditResult> preCommitAction,
            Consumer<WaypointEditResult> resultAction
    ) {
        return super.updateWaypoint(target, expectedSyncNum, patch, preCommitAction, result -> {
            try {
                resultAction.accept(result);
            } finally {
                if (result.status() == EditResultStatus.SUCCESS && runsWithClient) {
                    String dimensionName = target.dimensionName();
                    this.runOnClientThreadIfGenerationActive(dimensionName, result.fileManager(), () -> {
                        WaypointList list = result.fileManager().getWaypointListByName(target.listIdentifier());
                        SimpleWaypoint waypoint = list == null
                                ? null
                                : list.getWaypointByName(result.afterSnapshot().name());
                        if (waypoint != null && dimensionName.equals(WaypointClientMod.getCurrentDimensionName())) {
                            OptimizedWaypointRenderer.updateWaypoint(waypoint);
                        }
                        WaypointManagerScreen.updateWaypointWidget(dimensionName);
                        syncWaypointModification(
                                dimensionName,
                                target.listIdentifier(),
                                WaypointModificationType.UPDATE,
                                result.afterSnapshot(),
                                target.requiredWaypointIdentifier()
                        );
                    });
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
            List<String> keywords,
            String description,
            Consumer<UpdateWaypointResult> resultAction
    ) {
        return this.updateWaypointProperties(
                dimensionName,
                listName,
                oldName,
                newName,
                newName,
                initials,
                waypointPos,
                rgb,
                yaw,
                global,
                keywords,
                description,
                resultAction
        );
    }

    @Override
    public UpdateWaypointResult updateWaypointProperties(
            String dimensionName,
            String listName,
            String oldName,
            String newName,
            String displayName,
            String initials,
            WaypointPos waypointPos,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description,
            Consumer<UpdateWaypointResult> resultAction
    ) {
        return super.updateWaypointProperties(
                dimensionName,
                listName,
                oldName,
                newName,
                displayName,
                initials,
                waypointPos,
                rgb,
                yaw,
                global,
                keywords,
                description,
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
        return this.addWaypointList(dimensionName, listName, listName, resultAction);
    }

    @Override
    public AddWaypointListResult addWaypointList(
            String dimensionName,
            String listName,
            String displayName,
            Consumer<AddWaypointListResult> resultAction
    ) {
        return super.addWaypointList(dimensionName, listName, displayName, result -> {
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
        if (crossServer != null) crossServer.start(minecraftServer);
    }

    public void unload() {
        if (crossServer != null) crossServer.stop();
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
