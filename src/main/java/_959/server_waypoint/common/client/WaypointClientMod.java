package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.BufferHandler;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asClientFromRemoteServer;

public class WaypointClientMod extends WaypointFilesManagerCore implements BufferHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
    public static WaypointClientMod INSTANCE;
    private static String currentDimensionName;
    private static boolean handshakeFinished = false;
    private int remoteServerId;
    private final ClientHandshakeC2SPayload clientHandshake = new ClientHandshakeC2SPayload(new ClientHandshakeBuffer());
    private final WaypointFilesManagerCore localManager;
    private final Path gameRoot;
    private final MinecraftClient mc;

    public static void createInstance(MinecraftClient mc, Path gameRoot) {
        if (INSTANCE == null) {
            INSTANCE = new WaypointClientMod(mc, gameRoot);
            LOGGER.info("server_waypoint client initialized");
        }
    }

    public static WaypointClientMod getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("WaypointClient has not been initialized");
        return INSTANCE;
    }

    private WaypointClientMod(MinecraftClient mc, Path gameRoot) {
        super();
        this.mc = mc;
        this.gameRoot = gameRoot;
        this.localManager = new WaypointFilesManagerCore();
        INSTANCE = this;
    }

    @SuppressWarnings("unused")
    public boolean isHandshakeFinished() {
        return handshakeFinished;
    }

    public static void setHandshakeFinished(boolean isFinished) {
        handshakeFinished = isFinished;
    }

    public static String getCurrentDimensionName() {
        return currentDimensionName;
    }

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
        LOGGER.info("ids: {}", dimensionSyncIds);
        return new UpdateRequestC2SPayload(new ClientUpdateRequestBuffer(dimensionSyncIds));
    }

    /**
     * change the reference of {@link _959.server_waypoint.core.WaypointFilesManagerCore#fileManagerMap fileManagerMap} and release the old one
     * */
    public void changeFileManagerMap(LinkedHashMap<String, WaypointFileManager> fileManagerMap) {
        this.fileManagerMap = fileManagerMap;
    }

    @NonNull
    public List<WaypointList> getWaypointListsByDimensionName(String dimensionName) {
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        return fileManager == null ? new ArrayList<>() : fileManager.getWaypointLists();
    }

    /**
     * get an immutable sorted list of dimension names
     * */
    @NonNull
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

    @NonNull
    public @Unmodifiable List<WaypointList> getCurrentWaypointLists() {
        WaypointFileManager WaypointFileManager = this.fileManagerMap.get(currentDimensionName);
        if (WaypointFileManager == null) {
            return List.of();
        }
        return WaypointFileManager.getWaypointLists();
    }

    public static void onDimensionChange(String dimensionName) {
        currentDimensionName = dimensionName;
        if (handshakeFinished) {
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

    public void onJoinServer() {
        OptimizedWaypointRenderer.clearScene();
        if (this.mc.isConnectedToLocalServer()) {
            changeFileManagerMap(WaypointServerMod.getInstance().getFileManagerMap());
            OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
            this.waypointFilesDir = null;
            handshakeFinished = true;
        } else {
            // send handshake to server
            ClientPlayNetworking.send(clientHandshake);
        }
    }

    /**
     * can only be called when connected to a server
     * */
    public void requestUpdates(int serverId) {
        this.remoteServerId = serverId;
        ServerInfo currentServerEntry = this.mc.getCurrentServerEntry();
        if (currentServerEntry == null) {
            LOGGER.warn("current server entry is null");
            return;
        }
        changeWaypointFilesDir(asClientFromRemoteServer(this.gameRoot, currentServerEntry.address, serverId));
        ClientPlayNetworking.send(getClientUpdateRequestPayload());
    }

    @Override
    public void onServerHandshake(ServerHandshakeBuffer buffer) {
        LOGGER.info("received server handshake packet: {}", buffer.toString());
        this.requestUpdates(buffer.serverId());
    }

    @Override
    public void onUpdatesBundle(UpdatesBundleBuffer buffer) {
        LOGGER.info("received updates bundle: {}", buffer.toString());
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
                    LOGGER.info("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
                }
            }
        }
        handshakeFinished = true;
        OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
    }

    @Override
    public void onWaypointList(WaypointListBuffer buffer) {
        String dimensionName = buffer.dimensionName();
        WaypointFileManager fileManager = this.getOrCreateWaypointFileManager(dimensionName);
        fileManager.addWaypointList(buffer.waypointList());
    }

    @Override
    public void onDimensionWaypoint(DimensionWaypointBuffer buffer) {

    }

    @Override
    public void onWorldWaypoint(WorldWaypointBuffer buffer) {
        this.fileManagerMap.clear();
        OptimizedWaypointRenderer.clearScene();
        currentDimensionName = this.mc.world.getRegistryKey().getValue().toString();
        LOGGER.info("this dimension: {}", currentDimensionName);
        boolean found = false;
        for (DimensionWaypointBuffer dimensionWaypoint : buffer) {
            String dimensionName = dimensionWaypoint.dimensionName();
            LOGGER.info("dimension name: {}", dimensionName);
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
        WaypointManagerScreen.updateAll();
    }

    @Override
    public void onWaypointModification(WaypointModificationBuffer buffer) {
        if (WaypointServerMod.hasClient()) return;
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
                    WaypointManagerScreen.refreshWaypointLists(dimensionName);
                    fileManager.saveDimension();
                    OptimizedWaypointRenderer.add(waypoint);
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
                        OptimizedWaypointRenderer.remove(wpToRemove);
                        waypointList.remove(wpToRemove, buffer.syncId());
                        fileManager.saveDimension();
                        WaypointManagerScreen.refreshWaypointLists(dimensionName);
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
                    OptimizedWaypointRenderer.updateWaypoint(waypointFound);
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
}
