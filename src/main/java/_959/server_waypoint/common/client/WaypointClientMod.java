package _959.server_waypoint.common.client;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.BufferHandler;
import _959.server_waypoint.common.client.handlers.HandlerForXaerosMinimap;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import static _959.server_waypoint.ModInfo.MOD_ID;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asClientFromRemoteServer;

public class WaypointClientMod extends WaypointFilesManagerCore implements BufferHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
    public static boolean isXaerosMinimapReady = false;
    private static WaypointClientMod INSTANCE;
    private static ClientNetworkState networkState = ClientNetworkState.NOT_READY;
    private static String currentDimensionName;
    private static ClientConfig clientConfig;
    private final ClientHandshakeC2SPayload clientHandshake = new ClientHandshakeC2SPayload(new ClientHandshakeBuffer());
    // TODO: add a local waypoint manager for using waypoints on an unsupported server
//    private final WaypointFilesManagerCore localManager;
    private final Path gameRoot;
    private final Path configPath;
    private final MinecraftClient mc;

    public static void createInstance(MinecraftClient mc, Path gameRoot, Path configDir) {
        if (INSTANCE == null) {
            INSTANCE = new WaypointClientMod(mc, gameRoot, configDir);
            INSTANCE.loadConfig();
            LOGGER.info("server_waypoint client initialized");
        }
    }

    public static WaypointClientMod getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("WaypointClient has not been initialized");
        return INSTANCE;
    }

    public static ClientNetworkState getNetworkState() {
        return networkState;
    }

    private WaypointClientMod(MinecraftClient mc, Path gameRoot, Path configDir) {
        super();
        this.mc = mc;
        this.gameRoot = gameRoot;
        this.configPath = configDir.resolve(MOD_ID).resolve("client-config.json");
        INSTANCE = this;
    }

    private void resetNetworkState() {
        networkState = ClientNetworkState.NOT_READY;
    }

    private Gson getGson() {
        return new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    }

    public void loadConfig() {
        final Gson GSON = getGson();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                clientConfig = GSON.fromJson(reader, ClientConfig.class);
                OptimizedWaypointRenderer.enableRendering(clientConfig.isEnableWaypointRender());
                OptimizedWaypointRenderer.setWaypointScalingFactor(clientConfig.getWaypointScalingFactor());
                OptimizedWaypointRenderer.setWaypointVerticalOffset(clientConfig.getWaypointVerticalOffset());
                OptimizedWaypointRenderer.setWaypointBgAlpha(clientConfig.getWaypointBackgroundAlpha());
                OptimizedWaypointRenderer.setViewDistance(clientConfig.getViewDistance());
            } catch (IOException e) {
                LOGGER.error("Failed to load client config", e);
                clientConfig = GSON.fromJson("{}", ClientConfig.class);
            }
        } else {
            clientConfig = GSON.fromJson("{}", ClientConfig.class);
            saveConfig();
        }
    }

    public void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                final Gson GSON = getGson();
                GSON.toJson(clientConfig, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save client config", e);
        }
    }

    public static ClientConfig getClientConfig() {
        return clientConfig;
    }

    public static String getCurrentDimensionName() {
        return currentDimensionName;
    }

    @SuppressWarnings("unused")
    public boolean hasNoWaypoints() {
        if (this.fileManagerMap.isEmpty()) {
            return true;
        }
        for (WaypointFileManager manager : this.fileManagerMap.values()) {
            if (manager != null && !manager.hasNoWaypoints()) {
                return false;
            }
        }
        return true;
    }

    public void forEachWaypointFileManager(@NotNull Consumer<@NotNull WaypointFileManager> consumer) {
        for (WaypointFileManager fileManager : this.fileManagerMap.values()) {
            if (fileManager != null) consumer.accept(fileManager);
        }
    }

    public void removeDimension(String dimensionName) {
        this.fileManagerMap.get(dimensionName).deleteDimensionFile();
        this.fileManagerMap.remove(dimensionName);
    }

    public UpdateRequestC2SPayload getClientUpdateRequestPayload() {
        List<DimensionSyncIdentifier> dimensionSyncIds = new ArrayList<>();
        for (WaypointFileManager manager : this.fileManagerMap.values()) {
            if (manager == null) continue;
            String dimensionName = manager.getDimensionName();
            List<WaypointListSyncIdentifier> listSyncIds = new ArrayList<>();
            for (WaypointList waypointList : manager.getWaypointLists()) {
                listSyncIds.add(waypointList.getIdentifier());
            }
            dimensionSyncIds.add(new DimensionSyncIdentifier(dimensionName, listSyncIds));
        }
        return new UpdateRequestC2SPayload(new ClientUpdateRequestBuffer(dimensionSyncIds));
    }

    /**
     * change the reference of {@link _959.server_waypoint.core.WaypointFilesManagerCore#fileManagerMap fileManagerMap} and release the old one
     * */
    public void changeFileManagerMap(LinkedHashMap<String, WaypointFileManager> fileManagerMap) {
        this.fileManagerMap = fileManagerMap;
    }

    @NotNull
    public List<WaypointList> getWaypointListsByDimensionName(String dimensionName) {
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        return fileManager == null ? new ArrayList<>() : fileManager.getWaypointLists();
    }

    /**
     * get an immutable sorted list of dimension names
     * */
    @NotNull
    public @Unmodifiable List<String> getDimensionNames() {
        // keep the order of vanilla dimensions and sort the rest alphabetically
        int size = this.fileManagerMap.size();
        if (size <= 3) {
            return this.fileManagerMap.keySet().stream().toList();
        } else {
            List<String> dimensionNames = new ArrayList<>(this.fileManagerMap.keySet());
            dimensionNames.subList(3, size).sort(String::compareTo);
            return dimensionNames.stream().toList();
        }
    }

    @NotNull
    public @Unmodifiable List<WaypointList> getCurrentWaypointLists() {
        WaypointFileManager WaypointFileManager = this.fileManagerMap.get(currentDimensionName);
        if (WaypointFileManager == null) {
            return List.of();
        }
        return WaypointFileManager.getWaypointLists();
    }

    public static void onDimensionChange(String dimensionName) {
        currentDimensionName = dimensionName;
        if (networkState != ClientNetworkState.NOT_READY) {
            OptimizedWaypointRenderer.clearScene();
            WaypointFileManager WaypointFileManager = INSTANCE.fileManagerMap.get(dimensionName);
            if (WaypointFileManager == null || WaypointFileManager.hasNoWaypoints()) {
                return;
            }
            final List<WaypointList> waypointLists = WaypointFileManager.getWaypointLists();
            OptimizedWaypointRenderer.loadScene(waypointLists);
            WaypointManagerScreen.updateAll();
        }
    }

    public void onLeaveServer() {
        OptimizedWaypointRenderer.clearScene();
        if (!this.mc.isConnectedToLocalServer()) {
            this.saveAllWaypointFiles();
        }
        this.resetNetworkState();
    }

    public boolean loadCachedWaypointFiles(int serverId) {
        ServerInfo currentServerEntry = this.mc.getCurrentServerEntry();
        if (currentServerEntry == null) {
            LOGGER.warn("current server entry is null");
            return false;
        }
        changeWaypointFilesDir(asClientFromRemoteServer(this.gameRoot, currentServerEntry.address, serverId));
        return true;
    }

    /**
     * can only be called when connected to a server
     * */
    public void requestUpdates() {
        ClientPlayNetworking.send(getClientUpdateRequestPayload());
    }

    public void onJoinServer() {
        networkState = ClientNetworkState.NOT_READY;
        OptimizedWaypointRenderer.clearScene();
        if (this.mc.isConnectedToLocalServer()) {
            changeFileManagerMap(WaypointServerMod.getInstance().getFileManagerMap());
            OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
            this.waypointFilesDir = null;
            networkState = ClientNetworkState.SYNC_FINISHED;
        } else {
            // send handshake to server -> onServerHandShake
            networkState = ClientNetworkState.NO_SERVERSIDE_SUPPORT;
            ClientPlayNetworking.send(clientHandshake);
        }
    }

    @Override
    public void onServerHandshake(ServerHandshakeBuffer buffer) {
        networkState = ClientNetworkState.HANDSHAKE_FINISHED;
        int serverId = buffer.serverId();
        int serverVersion = buffer.version();
        if (serverVersion != ProtocolVersion.PROTOCOL_VERSION) {
            this.loadCachedWaypointFiles(serverId);
            networkState = ClientNetworkState.INCOMPATIBLE_PROTOCOL;
        } else if (this.loadCachedWaypointFiles(serverId)) {
            // send update requests to server -> onUpdatesBundle
            this.requestUpdates();
        }
    }

    @Override
    public void onUpdatesBundle(UpdatesBundleBuffer buffer) {
        for (DimensionWaypointBuffer dimensionBuffer : buffer) {
            String dimensionName = dimensionBuffer.dimensionName();
            WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
            List<WaypointList> listsUpdates = dimensionBuffer.waypointLists();
            if (listsUpdates.isEmpty()) {
                // remove dimension
                this.removeDimension(dimensionName);
            } else {
                // update dimension
                if (fileManager == null) {
                    fileManager = this.addWaypointListManager(dimensionName);
                    fileManager.addWaypointLists(listsUpdates);
                } else {
                    for (WaypointList listOnServer : listsUpdates) {
                        String listName = listOnServer.name();
                        WaypointList listOnClient = fileManager.getWaypointListByName(listName);
                        if (listOnServer.getSyncNum() == WaypointList.REMOVE_LIST) {
                            // remove list
                            if (listOnClient != null) {
                                fileManager.removeWaypointListByName(listName);
                            }
                        } else {
                            // replace list
                            fileManager.addWaypointList(listOnServer);
                        }
                    }
                }
                try {
                    fileManager.saveDimension();
                } catch (IOException e) {
                    LOGGER.error("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
                }
            }
        }
        if (clientConfig.isAutoSyncToXaerosMinimap() && isXaerosMinimapReady) {
            HandlerForXaerosMinimap.syncFromServerWaypointMod();
        }
        networkState = ClientNetworkState.SYNC_FINISHED;
        OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
    }

    @Override
    public void onWaypointList(WaypointListBuffer buffer) {
        if (WaypointServerMod.hasClient()) return;
        String dimensionName = buffer.dimensionName();
        boolean inCurrentDimension = currentDimensionName.equals(dimensionName);
        WaypointFileManager fileManager = this.getOrCreateWaypointFileManager(dimensionName);
        WaypointList newList = buffer.waypointList();
        WaypointList oldList = fileManager.getWaypointListByName(newList.name());
        fileManager.addWaypointList(newList);
        try {
            fileManager.saveDimension();
        } catch (IOException e) {
            LOGGER.error("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
            throw new RuntimeException(e);
        }
        if (inCurrentDimension) {
            if (oldList != null) OptimizedWaypointRenderer.removeList(oldList.simpleWaypoints());
            OptimizedWaypointRenderer.addList(newList.simpleWaypoints());
            WaypointManagerScreen.updateCurrentWaypointLists(fileManager.getWaypointLists());
        }
    }

    @Override
    public void onDimensionWaypoint(DimensionWaypointBuffer buffer) {
        if (WaypointServerMod.hasClient()) return;
        WaypointFileManager fileManager = this.fileManagerMap.get(buffer.dimensionName());
        if (fileManager == null) {
            fileManager = this.addWaypointListManager(buffer.dimensionName());
        }
        fileManager.addWaypointLists(buffer.waypointLists());
        try {
            fileManager.saveDimension();
        } catch (IOException e) {
            LOGGER.error("Failed to save dimension: {} at {}", buffer.dimensionName(), fileManager.getDimensionFile());
            throw new RuntimeException(e);
        }
        if (currentDimensionName.equals(buffer.dimensionName())) {
            OptimizedWaypointRenderer.clearScene();
            List<WaypointList> waypointLists = fileManager.getWaypointLists();
            OptimizedWaypointRenderer.loadScene(waypointLists);
            WaypointManagerScreen.updateCurrentWaypointLists(waypointLists);
        }
    }

    @Override
    public void onWorldWaypoint(WorldWaypointBuffer buffer) {
        if (WaypointServerMod.hasClient()) return;
        this.fileManagerMap.clear();
        OptimizedWaypointRenderer.clearScene();
        currentDimensionName = this.mc.world.getRegistryKey().getValue().toString();
        boolean found = false;
        for (DimensionWaypointBuffer dimensionWaypoint : buffer) {
            String dimensionName = dimensionWaypoint.dimensionName();
            WaypointFileManager fileManager = this.addWaypointListManager(dimensionName);
            List<WaypointList> waypointLists = dimensionWaypoint.waypointLists();
            if (!found && currentDimensionName.equals(dimensionName)) {
                found = true;
                for (WaypointList list : waypointLists) {
                    fileManager.addWaypointList(list);
                }
                OptimizedWaypointRenderer.loadScene(waypointLists);
            } else {
                for (WaypointList list : waypointLists) {
                    fileManager.addWaypointList(list);
                }
            }
            try {
                fileManager.saveDimension();
            } catch (IOException e) {
                LOGGER.error("failed to save waypoints for dimension: {}", dimensionName, e);
            }
        }
        networkState = ClientNetworkState.SYNC_FINISHED;
        WaypointManagerScreen.updateAll();
    }

    @Override
    public void onWaypointModification(WaypointModificationBuffer buffer) {
        if (WaypointServerMod.hasClient()) return;
        if (networkState != ClientNetworkState.SYNC_FINISHED) return;
        String dimensionName = buffer.dimensionName();
        String listName = buffer.listName();
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        WaypointModificationType modificationType = buffer.type();

        try {
            final SimpleWaypoint waypoint = buffer.waypoint();
            switch (modificationType) {
                case ADD -> {
                    if (fileManager == null) {
                        fileManager = this.addWaypointListManager(dimensionName);
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    int syncId = buffer.syncId();
                    if (waypointList == null) {
                        waypointList = WaypointList.build(listName, syncId);
                        fileManager.addWaypointList(waypointList);
                    }
                    waypointList.addFromRemoteServer(waypoint, syncId);
                    fileManager.saveDimension();
                    if (dimensionName.equals(currentDimensionName)) {
                        OptimizedWaypointRenderer.add(waypoint);
                        WaypointManagerScreen.refreshWaypointLists();
                    }
                }
                case REMOVE -> {
                    if (fileManager == null) {
                        return;
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        return;
                    }
                    String waypointName = buffer.waypointName();
                    SimpleWaypoint wpToRemove = waypointList.getWaypointByName(waypointName);
                    if (wpToRemove != null) {
                        waypointList.remove(wpToRemove, buffer.syncId());
                        fileManager.saveDimension();
                        if (dimensionName.equals(currentDimensionName)) {
                            WaypointManagerScreen.refreshWaypointLists();
                            OptimizedWaypointRenderer.remove(wpToRemove);
                        }
                    }
                }
                case UPDATE -> {
                    if (fileManager == null) {
                        return;
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        return;
                    }
                    SimpleWaypoint waypointFound = waypointList.getWaypointByName(buffer.waypointName());
                    if (waypointFound == null) {
                        return;
                    }
                    waypointFound.copyFrom(waypoint);
                    if (dimensionName.equals(currentDimensionName)) {
                        OptimizedWaypointRenderer.updateWaypoint(waypointFound);
                    }
                    waypointList.setSyncNum(buffer.syncId());
                    fileManager.saveDimension();
                }
                case ADD_LIST -> {
                    if (fileManager == null) {
                        fileManager = this.addWaypointListManager(dimensionName);
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        waypointList = WaypointList.buildByServer(listName);
                        fileManager.addWaypointList(waypointList);
                    }
                    WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
                    fileManager.saveDimension();
                }
                case REMOVE_LIST -> {
                    if (fileManager == null) {
                        return;
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        return;
                    } else {
                        fileManager.removeWaypointListByName(listName);
                    }
                    WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
                    fileManager.saveDimension();
                }
            }
        } catch (IOException e) {
            LOGGER.error("failed to save waypoints for dimension: {}", dimensionName, e);
        }
    }

    public enum ClientNetworkState {
        NOT_READY, // have not finished joining the server
        HANDSHAKE_FINISHED, // handshake finished, can start syncing waypoints
        SYNC_FINISHED, // syncing finished, allows all functionalities
        NO_SERVERSIDE_SUPPORT, // only loads local stored waypoint
        INCOMPATIBLE_PROTOCOL // can view previously cached waypoints but cannot handle packets from server
    }
}
