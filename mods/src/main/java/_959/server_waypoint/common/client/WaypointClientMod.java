package _959.server_waypoint.common.client;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.handlers.MessageHandler;
import _959.server_waypoint.common.client.integrations.ClientWaypointSyncEvent;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.MessageChunkC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import _959.server_waypoint.core.network.WaypointRevisionSequence;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.util.VanillaDimensionNames;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static _959.server_waypoint.ModInfo.MOD_ID;
import static _959.server_waypoint.common.client.util.NetworkHelper.sendPayloadToServer;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asClientFromRemoteServer;

public class WaypointClientMod extends WaypointFilesManagerCore implements MessageHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_client");
    public static boolean isXaerosMinimapReady = false;
    private static WaypointClientMod INSTANCE;
    private static ClientNetworkState networkState = ClientNetworkState.NOT_READY;
    private static String currentDimensionName;
    private static ClientConfig clientConfig;
    private final ClientHandshakeC2SPayload clientHandshake = new ClientHandshakeC2SPayload(new ClientHandshakeBuffer());
    private final ChunkedMessageManager<String> chunkedMessages = new ChunkedMessageManager<>();
    private boolean compressChunkedMessages;
    // TODO: add a local waypoint manager for using waypoints on an unsupported server
//    private final WaypointFilesManagerCore localManager;
    private final Path gameRoot;
    private final Path configPath;
    private final Path widgetThemePath;
    private final Minecraft mc;

    public static void createInstance(Minecraft mc, Path gameRoot, Path configDir) {
        if (INSTANCE == null) {
            INSTANCE = new WaypointClientMod(mc, gameRoot, configDir);
            INSTANCE.loadConfig();
            INSTANCE.loadWidgetTheme();
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

    private WaypointClientMod(Minecraft mc, Path gameRoot, Path configDir) {
        super();
        this.mc = mc;
        this.gameRoot = gameRoot;
        this.configPath = configDir.resolve(MOD_ID).resolve("client-config.json");
        this.widgetThemePath = configDir.resolve(MOD_ID).resolve("widget-theme.json");
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

    private void loadWidgetTheme() {
        WidgetThemeManager.resetTheme();
        if (!Files.exists(this.widgetThemePath)) {
            return;
        }
        try {
            WidgetThemeJson.loadAndApply(this.widgetThemePath);
        } catch (IOException | JsonParseException exception) {
            LOGGER.error("Failed to load widget theme from {}", this.widgetThemePath, exception);
            WidgetThemeManager.resetTheme();
        }
    }

    public Path getWidgetThemePath() {
        return this.widgetThemePath;
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
        this.removeWaypointFileManager(dimensionName, true);
    }

    public ClientUpdateRequestMessage getClientUpdateRequestMessage() {
        List<DimensionSyncIdentifier> dimensionSyncIds = new ArrayList<>(this.fileManagerMap.size());
        for (WaypointFileManager manager : this.fileManagerMap.values()) {
            if (manager == null) continue;
            String dimensionName = manager.getDimensionName();
            List<WaypointList> waypointLists = manager.getWaypointLists();
            List<WaypointListSyncIdentifier> listSyncIds = new ArrayList<>(waypointLists.size());
            for (WaypointList waypointList : waypointLists) {
                listSyncIds.add(waypointList.getIdentifier());
            }
            dimensionSyncIds.add(new DimensionSyncIdentifier(dimensionName, listSyncIds));
        }
        return new ClientUpdateRequestMessage(dimensionSyncIds);
    }

    /**
     * change the reference of {@link _959.server_waypoint.core.WaypointFilesManagerCore#fileManagerMap fileManagerMap} and release the old one
     * */
    public void changeFileManagerMap(Map<String, WaypointFileManager> fileManagerMap) {
        this.withLifecycleWriteLock(
                () -> this.fileManagerMap = Objects.requireNonNull(fileManagerMap, "fileManagerMap")
        );
    }

    @NotNull
    public @Unmodifiable List<WaypointList> getWaypointListsByDimensionName(String dimensionName) {
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        return fileManager == null ? List.of() : fileManager.getWaypointLists();
    }

    /**
     * get an immutable sorted list of dimension names
     * */
    @NotNull
    public @Unmodifiable List<String> getDimensionNames() {
        List<String> dimensionNames = new ArrayList<>(this.fileManagerMap.keySet());
        dimensionNames.sort(VanillaDimensionNames::dimensionNameComparator);
        return Collections.unmodifiableList(dimensionNames);
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
        LOGGER.info("dimensionName: {}, state: {}", dimensionName, networkState);
        if (networkState != ClientNetworkState.NOT_READY) {
            OptimizedWaypointRenderer.clearScene();
            WaypointFileManager waypointFileManager;
            if (WaypointServerMod.runsWithClient()) {
                waypointFileManager = WaypointServerMod.getInstance().getOrCreateWaypointFileManager(dimensionName);
            } else {
                waypointFileManager = INSTANCE.getOrCreateWaypointFileManager(dimensionName);
            }
            final List<WaypointList> waypointLists = waypointFileManager.getWaypointLists();
            OptimizedWaypointRenderer.loadScene(waypointLists);
            WaypointManagerScreen.updateAllWidgets();
        }
    }

    public void onLeaveServer() {
        this.chunkedMessages.clearAll();
        this.compressChunkedMessages = false;
        OptimizedWaypointRenderer.clearScene();
        if (!WaypointServerMod.runsWithClient()) {
            this.saveAllWaypointFiles();
        }
        this.resetNetworkState();
    }

    public boolean loadCachedWaypointFiles(int serverId) {
        ServerData currentServerEntry = this.mc.getCurrentServer();
        if (currentServerEntry == null) {
            LOGGER.warn("current server entry is null");
            return false;
        }
        changeWaypointFilesDir(asClientFromRemoteServer(this.gameRoot, currentServerEntry.ip, serverId));
        return true;
    }

    /**
     * can only be called when connected to a server
     * */
    public boolean requestUpdates() {
        return this.sendChunkedMessageToServer(this.getClientUpdateRequestMessage());
    }

    public void onJoinServer() {
        LOGGER.info("join server");
        this.chunkedMessages.clearAll();
        this.compressChunkedMessages = false;
        WaypointManagerScreen.resetSessionWidgetStates();
        networkState = ClientNetworkState.NOT_READY;
        OptimizedWaypointRenderer.clearScene();
        if (WaypointServerMod.runsWithClient()) {
            changeFileManagerMap(WaypointServerMod.getInstance().getFileManagerMap());
            OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
            this.waypointFilesDir = null;
            networkState = ClientNetworkState.SYNC_FINISHED;
        } else {
            // send handshake to server -> onServerHandShake
            networkState = ClientNetworkState.NO_SERVERSIDE_SUPPORT;
            sendPayloadToServer(clientHandshake);
        }
    }
    @Override
    public void onServerHandshake(ServerHandshakeBuffer buffer) {
        networkState = ClientNetworkState.HANDSHAKE_FINISHED;
        this.compressChunkedMessages = buffer.compressChunkedMessages();
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
    public void onMessageChunk(MessageChunkBuffer buffer) {
        try {
            for (ChunkedMessage message : this.chunkedMessages.receive(
                    "server",
                    buffer,
                    response -> sendPayloadToServer(new MessageChunkC2SPayload(response)),
                    this::recoverFromOrderedMessageFailure
            )) {
                this.applyChunkedMessage(message);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected malformed chunked-message transfer", exception);
        }
    }

    public boolean sendChunkedMessageToServer(ChunkedMessage message) {
        try {
            this.chunkedMessages.send(
                    "server",
                    message,
                    this.compressChunkedMessages,
                    packet -> sendPayloadToServer(new MessageChunkC2SPayload(packet))
            );
            return true;
        } catch (MessageEncodingException exception) {
            LOGGER.warn(
                    "Failed to encode client chunked message type {}",
                    message.getClass().getSimpleName(),
                    exception
            );
            this.displayNetworkError();
            if (message instanceof WaypointData waypointData
                    && waypointData.type() == WaypointData.Type.UPLOAD
                    && waypointData.uploadData().status()
                    != _959.server_waypoint.core.network.upload.UploadStatus.FAILED) {
                WaypointData.Upload upload = waypointData.uploadData();
                this.sendChunkedMessageToServer(WaypointData.upload(
                        upload.requestId(),
                        _959.server_waypoint.core.network.upload.UploadStatus.FAILED,
                        List.of()
                ));
            }
            return false;
        }
    }

    public void tickChunkedMessages() {
        this.chunkedMessages.tick();
    }

    public static void tickChunkedMessagesIfInitialized() {
        if (INSTANCE != null) {
            INSTANCE.tickChunkedMessages();
        }
    }

    private void applyChunkedMessage(ChunkedMessage message) {
        if (message instanceof WaypointData waypointData) {
            this.applyWaypointData(waypointData);
        } else if (message instanceof WaypointModificationMessage modification) {
            this.onWaypointModification(modification);
        } else if (message instanceof WaypointListUpdateMessage update) {
            this.onWaypointListUpdate(update);
        } else if (message instanceof WaypointEditResultMessage result) {
            this.onWaypointEditResult(result);
        } else {
            LOGGER.warn(
                    "Ignoring clientbound chunked message type {}",
                    message.getClass().getSimpleName()
            );
        }
    }

    private void applyWaypointData(WaypointData waypointData) {
        switch (waypointData.type()) {
            case UPDATES -> this.onUpdatesBundle(waypointData.dimensions());
            case DIMENSION -> this.onDimensionWaypoint(waypointData.singleDimension());
            case WAYPOINT_LIST -> this.onWaypointList(waypointData.singleDimension());
            case WORLD -> this.onWorldWaypoint(waypointData.dimensions());
            case UPLOAD -> LOGGER.warn("Ignoring upload waypoint data received from server");
        }
    }

    private void recoverFromOrderedMessageFailure() {
        LOGGER.warn("Ordered message delivery failed; requesting waypoint resynchronization");
        this.displayNetworkError();
        networkState = ClientNetworkState.HANDSHAKE_FINISHED;
        if (!this.requestUpdates()) {
            networkState = ClientNetworkState.SYNC_FINISHED;
        }
    }

    private void displayNetworkError() {
        this.mc.execute(() -> {
            if (this.mc.player != null) {
                net.minecraft.network.chat.Component message =
                        net.minecraft.network.chat.Component.translatable(
                                "waypoint.network.encoding_failed"
                        );
                //? if >=26
                this.mc.player.sendSystemMessage(message);
                //? if <26
                /*this.mc.player.displayClientMessage(message, false);*/
            }
        });
    }

    private void onUpdatesBundle(List<DimensionWaypointData> updates) {
        for (DimensionWaypointData dimensionBuffer : updates) {
            String dimensionName = dimensionBuffer.dimensionName();
            WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
            List<WaypointList> listsUpdates = dimensionBuffer.waypointLists();
            if (listsUpdates.isEmpty()) {
                // remove dimension
                this.removeDimension(dimensionName);
            } else {
                // update dimension
                if (fileManager == null) {
                    List<WaypointList> replacements = listsUpdates.stream()
                            .filter(waypointList -> waypointList.getSyncNum() != WaypointList.REMOVE_LIST)
                            .toList();
                    if (!replacements.isEmpty()) {
                        fileManager = this.putWaypointLists(dimensionName, replacements);
                    }
                } else {
                    for (WaypointList listOnServer : listsUpdates) {
                        String listName = listOnServer.name();
                        if (listOnServer.getSyncNum() == WaypointList.REMOVE_LIST) {
                            this.removeWaypointListImmediately(dimensionName, listName);
                        } else {
                            this.putWaypointList(dimensionName, listOnServer);
                        }
                    }
                }
                if (fileManager != null) {
                    try {
                        this.saveWaypointFile(fileManager);
                    } catch (IOException e) {
                        LOGGER.error("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
                    }
                }
            }
        }
        networkState = ClientNetworkState.SYNC_FINISHED;
        OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
        WaypointManagerScreen.updateAllWidgets();
        MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.allSynced(), this);
    }

    private void onWaypointList(DimensionWaypointData buffer) {
        if (WaypointServerMod.runsWithClient()) return;
        String dimensionName = buffer.dimensionName();
        boolean inCurrentDimension = currentDimensionName.equals(dimensionName);
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        boolean dimensionListChanged = fileManager == null;
        if (dimensionListChanged) {
            fileManager = this.addWaypointFileManager(dimensionName);
        }
        WaypointList newList = buffer.waypointLists().get(0);
        WaypointList oldList = fileManager.getWaypointListByName(newList.name());
        fileManager = this.putWaypointList(dimensionName, newList);
        try {
            this.saveWaypointFile(fileManager);
        } catch (IOException e) {
            LOGGER.error("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
            throw new RuntimeException(e);
        }
        if (inCurrentDimension) {
            if (oldList != null) OptimizedWaypointRenderer.removeList(oldList.simpleWaypoints());
            OptimizedWaypointRenderer.addList(newList.simpleWaypoints());
        }
        updateWaypointManagerView(dimensionName, dimensionListChanged);
        MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.listReplaced(dimensionName, newList), this);
    }

    private void onDimensionWaypoint(DimensionWaypointData buffer) {
        if (WaypointServerMod.runsWithClient()) return;
        String dimensionName = buffer.dimensionName();
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        boolean dimensionListChanged = fileManager == null;
        for (WaypointList waypointList : buffer.waypointLists()) {
            if (waypointList.getSyncNum() == WaypointList.REMOVE_LIST) {
                if (fileManager != null) {
                    this.removeWaypointListImmediately(dimensionName, waypointList.name());
                }
                continue;
            }
            fileManager = this.putWaypointList(dimensionName, waypointList);
        }
        List<WaypointList> currentLists = fileManager == null
                ? List.of()
                : fileManager.getWaypointLists();
        if (fileManager != null) {
            try {
                this.saveWaypointFile(fileManager);
            } catch (IOException e) {
                LOGGER.error("Failed to save dimension: {} at {}", dimensionName, fileManager.getDimensionFile());
                throw new RuntimeException(e);
            }
        }
        if (currentDimensionName.equals(dimensionName)) {
            OptimizedWaypointRenderer.clearScene();
            OptimizedWaypointRenderer.loadScene(currentLists);
        }
        updateWaypointManagerView(dimensionName, dimensionListChanged && fileManager != null);
        MapModIntegrations.onClientWaypointSync(
                ClientWaypointSyncEvent.dimensionReplaced(dimensionName, currentLists),
                this
        );
    }

    private void onWorldWaypoint(List<DimensionWaypointData> dimensions) {
        if (WaypointServerMod.runsWithClient()) return;
        if (this.mc.level == null) {
            LOGGER.error("ClientLevel is null at this time");
            return;
        }
        OptimizedWaypointRenderer.clearScene();
        updateDimensionName();
        Map<String, List<WaypointList>> replacement = new LinkedHashMap<>();
        List<WaypointList> currentWaypointLists = List.of();
        for (DimensionWaypointData dimensionWaypoint : dimensions) {
            String dimensionName = dimensionWaypoint.dimensionName();
            List<WaypointList> waypointLists = dimensionWaypoint.waypointLists();
            replacement.put(dimensionName, waypointLists);
            if (currentDimensionName.equals(dimensionName)) {
                currentWaypointLists = waypointLists;
            }
        }
        this.replaceWaypointData(replacement);
        OptimizedWaypointRenderer.loadScene(currentWaypointLists);
        this.saveAllWaypointFiles();
        networkState = ClientNetworkState.SYNC_FINISHED;
        WaypointManagerScreen.updateAllWidgets();
        MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.worldReplaced(), this);
    }

    private void updateDimensionName() {
        if (this.mc.level == null) {
            LOGGER.error("ClientLevel is null at this time");
            return;
        }
        //? if >= 1.21.11 {
        currentDimensionName = this.mc.level.dimension().identifier().toString();
        //?} else {
        /*currentDimensionName = this.mc.level.dimension().location().toString();
         *///?}
    }

    public void onWaypointModification(WaypointModificationMessage buffer) {
        if (WaypointServerMod.runsWithClient()) return;
        if (networkState != ClientNetworkState.SYNC_FINISHED) return;
        String dimensionName = buffer.dimensionName();
        String listName = buffer.listName();
        String listDisplayName = buffer.listDisplayName();
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        boolean dimensionListChanged = fileManager == null;
        WaypointModificationType modificationType = buffer.type();
        WaypointList currentList = fileManager == null
                ? null
                : fileManager.getWaypointListByName(listName);
        if (!this.shouldApplyIncrementalModification(buffer, currentList)) {
            return;
        }

        try {
            final SimpleWaypoint waypoint = buffer.waypoint();
            switch (modificationType) {
                case ADD -> {
                    int syncId = buffer.syncId();
                    SimpleWaypoint liveWaypoint = this.addWaypointFromRemoteServer(
                            dimensionName,
                            listName,
                            listDisplayName,
                            waypoint,
                            syncId
                    );
                    fileManager = this.getWaypointFileManager(dimensionName);
                    this.saveWaypointFile(fileManager);
                    if (dimensionName.equals(currentDimensionName)) {
                        OptimizedWaypointRenderer.add(liveWaypoint);
                    }
                    updateWaypointManagerView(dimensionName, dimensionListChanged);
                    MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, modificationType, liveWaypoint, liveWaypoint.name()), this);
                }
                case REMOVE -> {
                    if (fileManager == null) {
                        return;
                    }
                    String waypointName = buffer.waypointName();
                    SimpleWaypoint wpToRemove = this.removeWaypointFromRemoteServer(
                            dimensionName,
                            listName,
                            waypointName,
                            buffer.syncId()
                    );
                    if (wpToRemove != null) {
                        this.saveWaypointFile(fileManager);
                        if (dimensionName.equals(currentDimensionName)) {
                            OptimizedWaypointRenderer.remove(wpToRemove);
                        }
                        WaypointManagerScreen.updateWaypointWidget(dimensionName);
                        MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, modificationType, null, waypointName), this);
                    }
                }
                case UPDATE -> {
                    if (fileManager == null) {
                        return;
                    }
                    SimpleWaypoint waypointFound = this.updateWaypointFromRemoteServer(
                            dimensionName,
                            listName,
                            buffer.waypointName(),
                            waypoint,
                            buffer.syncId()
                    );
                    if (waypointFound == null) {
                        return;
                    }
                    if (dimensionName.equals(currentDimensionName)) {
                        OptimizedWaypointRenderer.updateWaypoint(waypointFound);
                    }
                    this.saveWaypointFile(fileManager);
                    WaypointManagerScreen.updateWaypointWidget(dimensionName);
                    MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, modificationType, waypointFound, buffer.waypointName()), this);
                }
                case ADD_LIST -> {
                    if (fileManager == null) {
                        fileManager = this.addWaypointFileManager(dimensionName);
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        waypointList = WaypointList.buildByServer(listName, listDisplayName);
                        fileManager = this.putWaypointList(dimensionName, waypointList);
                    }
                    updateWaypointManagerView(dimensionName, dimensionListChanged);
                    this.saveWaypointFile(fileManager);
                    MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, modificationType, null, null), this);
                }
                case REMOVE_LIST -> {
                    if (fileManager == null) {
                        return;
                    }
                    WaypointList waypointList = fileManager.getWaypointListByName(listName);
                    if (waypointList == null) {
                        return;
                    } else {
                        this.removeWaypointListImmediately(dimensionName, listName);
                    }
                    WaypointManagerScreen.updateWaypointWidget(dimensionName);
                    this.saveWaypointFile(fileManager);
                    MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.waypointModified(dimensionName, listName, modificationType, null, null), this);
                }
            }
        } catch (IOException e) {
            LOGGER.error("failed to save waypoints for dimension: {}", dimensionName, e);
        }
    }

    private boolean shouldApplyIncrementalModification(
            WaypointModificationMessage message,
            WaypointList currentList
    ) {
        if (currentList == null) {
            boolean completeCreation = message.type() == WaypointModificationType.ADD_LIST
                    && message.syncId() == WaypointList.SERVER_N;
            boolean firstWaypointCreation = message.type() == WaypointModificationType.ADD
                    && message.syncId() == WaypointList.SERVER_N + 1;
            if (completeCreation || firstWaypointCreation) {
                return true;
            }
            if (message.type() == WaypointModificationType.REMOVE_LIST) {
                return false;
            }
            this.requestRevisionRecovery(message.dimensionName(), message.listName());
            return false;
        }

        WaypointRevisionSequence.Decision decision = WaypointRevisionSequence.classify(
                currentList.getSyncNum(),
                message.syncId()
        );
        if (decision == WaypointRevisionSequence.Decision.STALE) {
            return false;
        }
        if (decision == WaypointRevisionSequence.Decision.GAP) {
            this.requestRevisionRecovery(message.dimensionName(), message.listName());
            return false;
        }
        return true;
    }

    private void requestRevisionRecovery(String dimensionName, String listName) {
        LOGGER.warn(
                "Waypoint revision gap for dimension {} list {}; requesting a complete snapshot",
                dimensionName,
                listName
        );
        networkState = ClientNetworkState.HANDSHAKE_FINISHED;
        if (!this.requestUpdates()) {
            networkState = ClientNetworkState.SYNC_FINISHED;
        }
    }

    public void onWaypointListUpdate(WaypointListUpdateMessage buffer) {
        if (WaypointServerMod.runsWithClient()) {
            return;
        }
        if (networkState != ClientNetworkState.SYNC_FINISHED) {
            return;
        }
        String dimensionName = buffer.dimensionName();
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            return;
        }
        WaypointList previous = fileManager.getWaypointListByName(buffer.previousListIdentifier());
        WaypointList updated = buffer.waypointList();
        WaypointList current = previous == null
                ? fileManager.getWaypointListByName(updated.name())
                : previous;
        if (current != null && updated.getSyncNum() <= current.getSyncNum()) {
            return;
        }
        if (previous != null) {
            this.removeWaypointListImmediately(dimensionName, buffer.previousListIdentifier());
        }
        fileManager = this.putWaypointList(dimensionName, updated);
        try {
            this.saveWaypointFile(fileManager);
        } catch (IOException exception) {
            LOGGER.error("Failed to save updated waypoint list in {}", dimensionName, exception);
        }
        if (dimensionName.equals(currentDimensionName)) {
            if (previous != null) {
                OptimizedWaypointRenderer.removeList(previous.simpleWaypoints());
            }
            OptimizedWaypointRenderer.addList(updated.simpleWaypoints());
        }
        WaypointManagerScreen.updateWidgetsForDimensionListChange(dimensionName);
        MapModIntegrations.onClientWaypointSync(
                ClientWaypointSyncEvent.listReplaced(dimensionName, updated),
                this
        );
    }

    public void onWaypointEditResult(WaypointEditResultMessage buffer) {
        WaypointEditScreen.handleResult(buffer);
    }

    /**
     * Routes a remote-server mutation to the smallest manager refresh that can represent it.
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

    public static @Unmodifiable List<String> getAllAvailableDimensionNames() {
        if (INSTANCE == null) return List.of();
        return List.copyOf(INSTANCE.fileManagerMap.keySet());
    }

    public static @Unmodifiable List<String> getAllWaypointListNames(String dimensionName) {
        if (INSTANCE == null) return List.of();
        WaypointFileManager fileManager = INSTANCE.fileManagerMap.get(dimensionName);
        if (fileManager == null) return List.of();
        List<WaypointList> lists = fileManager.getWaypointLists();
        List<String> names = new ArrayList<>(lists.size());
        for (WaypointList list : lists) {
            names.add(list.name());
        }
        return Collections.unmodifiableList(names);
    }

    public static @Unmodifiable List<String> getAllWaypointNames(String dimensionName, String listName) {
        if (INSTANCE == null) return List.of();
        WaypointFileManager fileManager = INSTANCE.fileManagerMap.get(dimensionName);
        if (fileManager == null) return List.of();
        WaypointList list = fileManager.getWaypointListByName(listName);
        if (list == null) return List.of();
        List<String> names = new ArrayList<>(list.size());
        for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
            names.add(waypoint.name());
        }
        return Collections.unmodifiableList(names);
    }

    public enum ClientNetworkState {
        NOT_READY, // have not finished joining the server
        HANDSHAKE_FINISHED, // handshake finished, can start syncing waypoints
        SYNC_FINISHED, // syncing finished, allows all functionalities
        NO_SERVERSIDE_SUPPORT, // only loads local stored waypoint
        INCOMPATIBLE_PROTOCOL // can view previously cached waypoints but cannot handle packets from server
    }
}
