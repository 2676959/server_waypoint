package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.BufferHandler;
import _959.server_waypoint.common.client.render.WaypointRenderData;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static _959.server_waypoint.common.client.render.WaypointRenderer.WaypointsOnHud;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asClientFromRemoteServer;

public class WaypointClientMod extends WaypointFilesManagerCore implements BufferHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
    public static WaypointClientMod INSTANCE;
    private static String currentDimensionName;
    private boolean handshakeFinished = false;
    private int remoteServerId;
    private final ClientHandshakeC2SPayload clientHandshake = new ClientHandshakeC2SPayload(new ClientHandshakeBuffer());
    private final WaypointFilesManagerCore localManager;
    private final Path gameRoot;
    private final MinecraftClient mc;

    public static void createInstance(MinecraftClient mc, Path gameRoot) {
        if (INSTANCE == null) {
            INSTANCE = new WaypointClientMod(mc, gameRoot);
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

    public void setHandshakeFinished(boolean handshakeFinished) {
        this.handshakeFinished = handshakeFinished;
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

    @NotNull
    public List<WaypointList> getWaypointListsByDimensionName(String dimensionName) {
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        return fileManager == null ? new ArrayList<>() : fileManager.getWaypointLists();
    }

    /**
     * get a sorted list of dimension names
     * */
    @NotNull
    public List<String> getDimensionNames() {
        List<String> dimensionNames = new ArrayList<>(this.fileManagerMap.keySet());
        // keep the order of vanilla dimensions and sort the rest alphabetically
        int size = dimensionNames.size();
        if (size <= 3) {
            return dimensionNames;
        }
        dimensionNames.subList(3, size).sort(String::compareTo);
        return dimensionNames;
    }

    @NotNull
    public List<WaypointList> getDefaultWaypointLists() {
        List<WaypointList> defaultWaypointLists = getCurrentWaypointLists();
        if (defaultWaypointLists.isEmpty()) {
            if (this.fileManagerMap.isEmpty()) {
                return new ArrayList<>();
            } else {
                WaypointFileManager fileManager = this.fileManagerMap.values().iterator().next();
                if (fileManager == null) {
                    return new ArrayList<>();
                } else {
                    return fileManager.getWaypointLists();
                }
            }
        }
        return defaultWaypointLists;
    }

    @NotNull
    public List<WaypointList> getCurrentWaypointLists() {
        WaypointFileManager WaypointFileManager = this.fileManagerMap.get(currentDimensionName);
        if (WaypointFileManager == null) {
            return new ArrayList<>();
        }
        return WaypointFileManager.getWaypointLists();
    }

    public static void onDimensionChange(String dimensionName) {
        WaypointFileManager WaypointFileManager = INSTANCE.fileManagerMap.get(dimensionName);
        currentDimensionName = dimensionName;
        if (WaypointFileManager == null) {
            WaypointsOnHud.clear();
            return;
        }
        final List<WaypointList> waypointLists = WaypointFileManager.getWaypointLists();
        WaypointsOnHud.clear();
        for (WaypointList waypointList : waypointLists) {
            String listName = waypointList.name();
            for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                WaypointRenderData renderData = WaypointRenderData.from(listName, waypoint);
                WaypointsOnHud.add(renderData);
            }
        }
        WaypointManagerScreen.updateAll();
    }

    public void onJoinServer() {
        if (this.mc.isConnectedToLocalServer()) {
            changeFileManagerMap(WaypointServerMod.getInstance().getFileManagerMap());
            this.waypointFilesDir = null;
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
        this.setHandshakeFinished(true);
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
        WaypointsOnHud.clear();
        currentDimensionName = this.mc.world.getRegistryKey().getValue().toString();
        LOGGER.info("this dimension: {}", currentDimensionName);
        boolean found = false;
        for (DimensionWaypointBuffer dimensionWaypoint : buffer) {
            String dimensionName = dimensionWaypoint.dimensionName();
            LOGGER.info("dimension name: {}", dimensionName);
            WaypointFileManager fileManager = this.addWaypointListManager(dimensionName);
            if (!found && currentDimensionName.equals(dimensionName)) {
                found = true;
                for (WaypointList list : dimensionWaypoint.waypointLists()) {
                    String listName = list.name();
                    fileManager.addWaypointList(list);
                    for (SimpleWaypoint simpleWaypoint : list.simpleWaypoints()) {
                        WaypointRenderData renderData = WaypointRenderData.from(listName, simpleWaypoint);
                        WaypointsOnHud.add(renderData);
                    }
                }
            } else {
                for (WaypointList list : dimensionWaypoint.waypointLists()) {
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
        String dimensionName = buffer.dimensionName();
        String listName = buffer.listName();
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        try {
            switch (buffer.type()) {
                case ADD -> {
                    if (!WaypointServerMod.isDedicated) {
                        WaypointManagerScreen.refreshWaypointLists(dimensionName);
                        return;
                    }
                    if (fileManager == null) {
                        fileManager = this.addWaypointListManager(dimensionName);
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    int syncId = buffer.syncId();
                    if (waypointList == null) {
                        waypointList = WaypointList.build(listName, syncId);
                        fileManager.addWaypointList(waypointList);
                    }
                    waypointList.addFromRemoteServer(buffer.waypoint(), syncId);
                    WaypointManagerScreen.refreshWaypointLists(dimensionName);
                    fileManager.saveDimension();
                }
                case REMOVE -> {
                    if (!WaypointServerMod.isDedicated) {
                        WaypointManagerScreen.refreshWaypointLists(dimensionName);
                        return;
                    }
                    if (fileManager == null) {
                        return;
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        return;
                    }
                    String waypointName = buffer.waypointName();
                    waypointList.removeByName(waypointName, buffer.syncId());
                    WaypointManagerScreen.refreshWaypointLists(dimensionName);
                    fileManager.saveDimension();
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
                    waypointFound.copyFrom(buffer.waypoint());
                    waypointList.setSyncNum(buffer.syncId());
                    fileManager.saveDimension();
                }
                case ADD_LIST -> {
                    if (fileManager == null) {
                        fileManager = this.addWaypointListManager(dimensionName);
                    }
                    if (!WaypointServerMod.isDedicated) {
                        WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
                        return;
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
                    if (!WaypointServerMod.isDedicated) {
                        WaypointManagerScreen.updateWaypointLists(dimensionName, fileManager.getWaypointLists());
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
        // update render data
        if (currentDimensionName.equals(dimensionName)) {
            WaypointsOnHud.clear();
            for (WaypointList waypointList : fileManager.getWaypointLists()) {
                String list = waypointList.name();
                for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
                    WaypointRenderData renderData = WaypointRenderData.from(list, simpleWaypoint);
                    WaypointsOnHud.add(renderData);
                }
            }
        }
    }
}
