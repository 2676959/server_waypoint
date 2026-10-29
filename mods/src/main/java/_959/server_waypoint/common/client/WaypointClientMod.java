package _959.server_waypoint.common.client;

import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.common.client.integrations.IntegratedServerUpload;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.handlers.MessageHandler;
import _959.server_waypoint.common.client.integrations.ClientWaypointSyncEvent;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.MessageChunkC2SPayload;
import _959.server_waypoint.common.network.payload.c2s.UploadChunkC2SPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageRegistry;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveException;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveFailure;
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
import net.minecraft.world.level.storage.LevelResource;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final RemoteClientCatalogs remoteCatalogs = new RemoteClientCatalogs();
    public RemoteClientCatalogs remoteCatalogs() { return this.remoteCatalogs; }

    private final ClientHandshakeC2SPayload clientHandshake = new ClientHandshakeC2SPayload(new ClientHandshakeBuffer());
    private final ChunkedMessageManager<String> chunkedMessages = new ChunkedMessageManager<>();
    private final ChunkedMessageManager<String> uploadChunkedMessages = new ChunkedMessageManager<>();
    private final ClientWaypointCollections collections = new ClientWaypointCollections(this);
    private final ClientSynchronizationTracker synchronizationTracker = collections.tracker(ClientWaypointSource.SHARED);

    public ClientWaypointCollections collections() { return this.collections; }
    private boolean compressChunkedMessages;
    private final LocalWaypointProfiles localProfiles;
    private final ClientWaypointAccess access;

    public ClientWaypointAccess access() { return this.access; }

    public LocalWaypointProfiles localProfiles() { return this.localProfiles; }
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

    /**
     * return false if there is no instance of WaypointClientMod
     * */
    public static boolean ifPresent(Consumer<WaypointClientMod> action) {
        if (INSTANCE == null) return false;
        action.accept(INSTANCE);
        return true;
    }

    public static ClientNetworkState getNetworkState() {
        return networkState;
    }

    private WaypointClientMod(Minecraft mc, Path gameRoot, Path configDir) {
        super();
        this.mc = mc;
        this.gameRoot = gameRoot;
        // Dedicated servers exclude these fields. A standalone client also needs to retain Local
        // visibility/expansion without waiting for integrated-server initialization.
        WaypointList.excludeClientOnlyFields = false;
        this.localProfiles = new LocalWaypointProfiles(gameRoot, this.collections);
        this.access = new ClientWaypointAccess(this.collections, this.localProfiles, new ClientWaypointAccess.Transport() {
            public boolean connected() { return mc.getConnection() != null; }
            public String currentDimension() { return currentDimensionName; }
            public int commandLimit() {
                //? if <1.20.5 {
                /*return 256;
                *///?} else {
                return 32767;
                //?}
            }
            public List<String> dimensions() {
                var connection = mc.getConnection();
                if (connection == null) return List.of();
                return connection.levels().stream().map(key ->
                        //? if >=1.21.11 {
                        key.identifier().toString()
                        //?} else {
                        /*key.location().toString()
                        *///?}
                ).toList();
            }
            public boolean allowed(ClientWaypointSource source, ClientWaypointAccess.Action action) {
                var connection = mc.getConnection();
                if (connection == null) return false;
                var root = connection.getCommands().getRoot().getChild("wp");
                if (root != null && source == ClientWaypointSource.PRIVATE) root = root.getChild("private");
                String name = switch (action) {
                    case READ -> "list";
                    case TELEPORT -> "tp";
                    default -> action.name().toLowerCase(java.util.Locale.ROOT);
                };
                return root != null && root.getChild(name) != null;
            }
            public boolean command(String command) { return _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand(command); }
            public boolean edit(WaypointEditRequestMessage message) { return sendChunkedMessageToServer(message); }
        });
        this.configPath = configDir.resolve(MOD_ID).resolve("client-config.json");
        this.widgetThemePath = configDir.resolve(MOD_ID).resolve("widget-theme.json");
        INSTANCE = this;
        IntegratedServerUpload.register(mc);
    }

    private void resetNetworkState() {
        networkState = ClientNetworkState.NOT_READY;
        this.synchronizationTracker.clearAll();
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
        return new ClientUpdateRequestMessage(WaypointScope.SHARED, dimensionSyncIds);
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
        // Local persistence has no dependency on a server handshake or Shared rendering readiness.
        if (INSTANCE != null) INSTANCE.saveLocalProfiles();
        if (networkState != ClientNetworkState.NOT_READY) {
            OptimizedWaypointRenderer.clearScene();
            final List<WaypointList> waypointLists;
            if (WaypointServerMod.runsWithClient()) {
                waypointLists = WaypointServerMod.getInstance()
                        .getOrCreateWaypointFileManager(dimensionName)
                        .getWaypointLists();
            } else {
                WaypointFileManager waypointFileManager = INSTANCE.getWaypointFileManager(dimensionName);
                // The waypoint files directory may not be loaded yet when the dimension changes right after a
                // server transfer (e.g. via Velocity): the server handshake/sync has not completed, so there is
                // nothing to render. Creating a manager here would NPE against the not-yet-set directory.
                waypointLists = waypointFileManager == null ? List.of() : waypointFileManager.getWaypointLists();
            }
            OptimizedWaypointRenderer.loadScene(waypointLists);
            WaypointManagerScreen.updateAllWidgets();
        }
    }

    public void onLeaveServer() {
        try {
            this.localProfiles.leave();
        } catch (IOException exception) {
            this.displayLocalStorageError(exception);
        }
        this.collections.disconnect();
        isXaerosMinimapReady = false;
        MapModIntegrations.clearPendingSync();
        this.remoteCatalogs.clear();
        this.chunkedMessages.clearAll();
        this.uploadChunkedMessages.clearAll();
        this.synchronizationTracker.clearAll();
        this.compressChunkedMessages = false;
        OptimizedWaypointRenderer.clearScene();
        if (!WaypointServerMod.runsWithClient()) {
            this.saveAllWaypointFiles();
        }
        this.resetNetworkState();
    }

    public void onShutdown() {
        this.onLeaveServer();
    }

    private void saveLocalProfiles() {
        try {
            this.localProfiles.save();
        } catch (IOException exception) {
            this.displayLocalStorageError(exception);
        }
    }

    private void displayLocalStorageError(Exception exception) {
        LOGGER.error("Cannot load or save Local waypoint profiles", exception);
        if (this.mc != null && this.mc.player != null) {
            var message = net.minecraft.network.chat.Component.translatable("waypoint.local.storage_failed");
            //? if >=26
            this.mc.player.sendSystemMessage(message);
            //? if <26
            /*this.mc.player.displayClientMessage(message, false);*/
        }
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
        boolean shared = this.sendChunkedMessageToServer(this.getClientUpdateRequestMessage());
        boolean privateRequested = this.sendChunkedMessageToServer(this.collections.updateRequest(ClientWaypointSource.PRIVATE));
        return shared && privateRequested;
    }

    public void onJoinServer() {
        isXaerosMinimapReady = false;
        MapModIntegrations.clearPendingSync();
        LOGGER.info("join server");
        ServerData serverEntry = this.mc.getCurrentServer();
        this.collections.beginConnection(this.mc.getUser().getProfileId(),
                serverEntry == null ? "integrated" : serverEntry.ip);
        try {
            var integratedServer = this.mc.getSingleplayerServer();
            if (integratedServer != null) {
                this.localProfiles.joinSingleplayer(this.mc.getUser().getProfileId(), integratedServer.getWorldPath(LevelResource.ROOT));
            } else if (serverEntry != null) {
                this.localProfiles.joinMultiplayer(this.mc.getUser().getProfileId(), serverEntry.ip);
            }
        } catch (IOException | IllegalArgumentException exception) {
            // Retain an unsaved outgoing store for retry, but beginConnection has invalidated its
            // selections so it cannot receive new-world edits after a failed transition.
            this.displayLocalStorageError(exception);
        }
        // Switching servers through a proxy (e.g. Velocity) does not trigger the disconnect that normally runs
        // onLeaveServer(), so flush any pending edits from the previous server to its own file before we
        // rebind to the new server. Otherwise edits are only saved at the final real disconnect.
        if (!WaypointServerMod.runsWithClient()) {
            this.saveAllWaypointFiles();
        }
        this.remoteCatalogs.clear();
        this.chunkedMessages.clearAll();
        this.uploadChunkedMessages.clearAll();
        this.synchronizationTracker.clearAll();
        this.compressChunkedMessages = false;
        WaypointManagerScreen.resetSessionWidgetStates();
        networkState = ClientNetworkState.NOT_READY;
        OptimizedWaypointRenderer.clearScene();
        if (WaypointServerMod.runsWithClient()) {
            changeFileManagerMap(WaypointServerMod.getInstance().getFileManagerMap());
            OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
            this.waypointFilesDir = null;
            networkState = ClientNetworkState.SYNC_FINISHED;
            // Shared waypoint storage still needs transport negotiation for edit requests/results.
            sendPayloadToServer(clientHandshake);
        } else {
            // A server's id is only known after its plugin answers the handshake, so reset the server-local
            // caches now. A server without the plugin never answers, and without this reset it would keep
            // reading from and writing into the previous server's cache directory (cross-server data
            // pollution). The directory and map are bound again once the new server's handshake arrives.
            this.fileManagerMap = new ConcurrentHashMap<>();
            this.waypointFilesDir = null;
            // send handshake to server -> onServerHandShake
            networkState = ClientNetworkState.NO_SERVERSIDE_SUPPORT;
            sendPayloadToServer(clientHandshake);
        }
    }
    @Override
    public void onServerHandshake(ServerHandshakeBuffer buffer) {
        this.onServerHandshake(buffer, this.collections.generation());
    }

    public void onServerHandshake(ServerHandshakeBuffer buffer, long receivedGeneration) {
        if (receivedGeneration != this.collections.generation()) return;
        this.remoteCatalogs.clear();
        this.compressChunkedMessages = buffer.compressChunkedMessages();
        if (buffer.version() != ProtocolVersion.PROTOCOL_VERSION) {
            this.collections.serverUnavailable();
            this.chunkedMessages.clearAll();
            networkState = ClientNetworkState.INCOMPATIBLE_PROTOCOL;
            return;
        }
        try {
            this.collections.bindServer(this.gameRoot, buffer.serverId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot bind private client collection", exception);
            networkState = ClientNetworkState.NOT_READY;
            return;
        }
        if (this.collections.ready(ClientWaypointSource.LOCAL)) {
            try {
                this.localProfiles.onBackendIdentity(buffer.serverId());
            } catch (IOException exception) {
                this.displayLocalStorageError(exception);
            }
        }
        if (WaypointServerMod.runsWithClient()) {
            // Shared keeps the integrated model. Private always comes through the local player's
            // authenticated connection; never borrow or expose the server's owner-manager registry.
            this.collections.markSnapshotApplied(ClientWaypointSource.SHARED);
            networkState = ClientNetworkState.SYNC_FINISHED;
            this.sendChunkedMessageToServer(this.collections.updateRequest(ClientWaypointSource.PRIVATE));
            return;
        }
        networkState = ClientNetworkState.HANDSHAKE_FINISHED;
        this.saveAllWaypointFiles();
        this.chunkedMessages.clearAll();
        this.uploadChunkedMessages.clearAll();
        OptimizedWaypointRenderer.clearScene();
        if (this.loadCachedWaypointFiles(buffer.serverId())) this.requestUpdates();
    }

    @Override
    public void onMessageChunk(MessageChunkBuffer buffer) {
        this.onMessageChunk(buffer, this.collections.generation());
    }

    public void onMessageChunk(MessageChunkBuffer buffer, long receivedGeneration) {
        if (receivedGeneration != this.collections.generation()) return;
        if ((networkState != ClientNetworkState.HANDSHAKE_FINISHED
                && networkState != ClientNetworkState.SYNC_FINISHED)
                || !isAllowedClientboundMessageType(buffer.messageTypeId())) {
            return;
        }
        try {
            this.chunkedMessages.receiveAndApply("server", buffer, message -> {
                if (message instanceof WaypointData waypointData
                        && waypointData.type() == WaypointData.Type.UPLOAD) {
                    throw new IllegalArgumentException(
                            "Upload waypoint data is not valid on the clientbound channel"
                    );
                }
                this.applyChunkedMessage(message);
            });
        } catch (ReceiveException exception) {
            this.handleChunkedReceiveFailure(exception.messageTypeId());
            LOGGER.warn(
                    "Rejected clientbound chunked-message type {}: {}",
                    exception.messageTypeId(),
                    exception.reason(),
                    exception
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected decoded clientbound chunked message", exception);
        }
    }

    public boolean sendChunkedMessageToServer(ChunkedMessage message) {
        long generation = this.collections.generation();
        try {
            if (message instanceof WaypointData waypointData
                    && waypointData.type() == WaypointData.Type.UPLOAD) {
                UUID requestId = waypointData.uploadData().requestId();
                ChunkedMessageDelivery delivery = this.uploadChunkedMessages.sendTracked(
                        "server",
                        message,
                        this.compressChunkedMessages,
                        packets -> {
                            if (generation != this.collections.generation()) return CompletableFuture.completedFuture(ChunkedMessageSendResult.UNSUPPORTED);
                            for (MessageChunkBuffer packet : packets) {
                                sendPayloadToServer(new UploadChunkC2SPayload(
                                        new UploadChunkBuffer(requestId, packet)
                                ));
                            }
                            return CompletableFuture.completedFuture(
                                    ChunkedMessageSendResult.DELIVERED
                            );
                        }
                );
                this.trackChunkedSend(message, delivery);
                return this.handleChunkedSendResult(message, delivery.admissionResult());
            }
            ChunkedMessageDelivery delivery = this.chunkedMessages.sendTracked(
                    "server",
                    message,
                    this.compressChunkedMessages,
                    packets -> {
                        if (generation != this.collections.generation()) return CompletableFuture.completedFuture(ChunkedMessageSendResult.UNSUPPORTED);
                        for (MessageChunkBuffer packet : packets) {
                            sendPayloadToServer(new MessageChunkC2SPayload(packet));
                        }
                        return CompletableFuture.completedFuture(
                                ChunkedMessageSendResult.DELIVERED
                        );
                    }
            );
            this.trackChunkedSend(message, delivery);
            return this.handleChunkedSendResult(message, delivery.admissionResult());
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
                        WaypointScope.SHARED, upload.requestId(),
                        _959.server_waypoint.core.network.upload.UploadStatus.FAILED,
                        List.of()
                ));
            }
            return false;
        }
    }

    private void trackChunkedSend(
            ChunkedMessage message,
            ChunkedMessageDelivery delivery
    ) {
        if (!delivery.queued()) {
            return;
        }
        long generation = this.collections.generation();
        delivery.completion().whenComplete((result, exception) -> {
            if (generation != this.collections.generation()) return;
            if (exception == null && result != null && result.delivered()) {
                return;
            }
            LOGGER.warn(
                    "Chunked message type {} failed after admission: {}",
                    message.getClass().getSimpleName(),
                    exception == null ? result : exception.getClass().getSimpleName()
            );
            this.displayNetworkError();
            if (message instanceof WaypointData waypointData
                    && waypointData.type() == WaypointData.Type.UPLOAD
                    && waypointData.uploadData().status()
                    != _959.server_waypoint.core.network.upload.UploadStatus.FAILED) {
                this.mc.execute(() -> {
                    if (generation == this.collections.generation()) this.sendChunkedMessageToServer(WaypointData.upload(
                            WaypointScope.SHARED, waypointData.uploadData().requestId(),
                            _959.server_waypoint.core.network.upload.UploadStatus.FAILED, List.of()));
                });
            }
        });
    }

    public void tickChunkedMessages() {
        MapModIntegrations.tickPendingSync(this);
        if (!WaypointServerMod.runsWithClient() && (networkState == ClientNetworkState.HANDSHAKE_FINISHED
                || networkState == ClientNetworkState.SYNC_FINISHED)) {
            var request = this.remoteCatalogs.poll();
            if (request != null) this.sendChunkedMessageToServer(request);
        }
        for (ReceiveFailure<String> failure : this.chunkedMessages.tick()) {
            this.handleChunkedReceiveFailure(failure.messageTypeId());
            LOGGER.warn(
                    "Discarded incomplete clientbound chunked-message type {}: {} (transfer {})",
                    failure.messageTypeId(),
                    failure.reason(),
                    failure.transferId().map(Object::toString).orElse("unknown")
            );
        }
        this.uploadChunkedMessages.tick();
    }

    private boolean handleChunkedSendResult(
            ChunkedMessage message,
            ChunkedMessageSendResult result
    ) {
        if (result.queued()) {
            return true;
        }
        LOGGER.warn(
                "Chunked message type {} was not queued: {}",
                message.getClass().getSimpleName(),
                result
        );
        this.displayNetworkError();
        return false;
    }

    public static void tickChunkedMessagesIfInitialized() {
        if (INSTANCE != null) {
            INSTANCE.access.tick();
            INSTANCE.tickChunkedMessages();
        }
    }

    private void applyChunkedMessage(ChunkedMessage message) {
        if (message instanceof WaypointEditResultMessage result) {
            this.onWaypointEditResult(result);
            return;
        }
        if (message instanceof _959.server_waypoint.core.network.WaypointScopedMessage scoped
                && (scoped.scope() == WaypointScope.PRIVATE || scoped instanceof WaypointCollectionStateMessage)) {
            this.applyCollectionMessage(scoped);
            return;
        }
        if (message instanceof RemoteCatalogMessage remote) {
            this.remoteCatalogs.apply(remote);
        } else if (message instanceof WaypointData waypointData) {
            this.applyWaypointData(waypointData);
        } else if (message instanceof WaypointModificationMessage modification) {
            this.onWaypointModification(modification);
        } else if (message instanceof WaypointListUpdateMessage update) {
            this.onWaypointListUpdate(update);
        } else {
            LOGGER.warn(
                    "Ignoring clientbound chunked message type {}",
                    message.getClass().getSimpleName()
            );
        }
    }

    private void applyCollectionMessage(_959.server_waypoint.core.network.WaypointScopedMessage message) {
        try {
            this.collections.apply(message, this.collections.generation());
        } catch (IOException | RuntimeException exception) {
            this.collections.tracker(ClientWaypointSource.fromScope(message.scope())).markUncertain();
            LOGGER.warn("Cannot apply client collection message", exception);
        }
    }

    private static boolean isAllowedClientboundMessageType(int messageTypeId) {
        return messageTypeId == ChunkedMessageRegistry.WAYPOINT_DATA.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_EDIT_RESULT.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_MODIFICATION.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_LIST_UPDATE.id()
                || messageTypeId == ChunkedMessageRegistry.REMOTE_CATALOG.id()
                || messageTypeId == ChunkedMessageRegistry.WAYPOINT_COLLECTION_STATE.id();
    }

    private void applyWaypointData(WaypointData waypointData) {
        switch (waypointData.type()) {
            case UPDATES -> this.onUpdatesBundle(waypointData.dimensions());
            case DIMENSION -> this.onDimensionWaypoint(waypointData.singleDimension());
            case WAYPOINT_LIST -> this.onWaypointList(waypointData.singleDimension());
            case WORLD -> this.onWorldWaypoint(waypointData.dimensions());
            case WAYPOINT -> this.onWaypointDownload(waypointData.singleDimension());
            case UPLOAD -> LOGGER.warn("Ignoring upload waypoint data received from server");
        }
    }

    private void handleChunkedReceiveFailure(int messageTypeId) {
        // A failed fragment does not expose its decoded scope. Both server trackers need recovery.
        this.synchronizationTracker.markTransportFailure(messageTypeId);
        this.collections.tracker(ClientWaypointSource.PRIVATE).markTransportFailure(messageTypeId);
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
        this.collections.markSnapshotApplied(ClientWaypointSource.SHARED);
        this.synchronizationTracker.clearAll();
        OptimizedWaypointRenderer.loadScene(getCurrentWaypointLists());
        WaypointManagerScreen.updateAllWidgets();
        if (!updates.isEmpty()) {
            MapModIntegrations.onClientWaypointSync(ClientWaypointSyncEvent.allSynced(), this);
        } else {
            MapModIntegrations.onUnchangedClientWaypointSync(this);
        }
    }

    private void onWaypointList(DimensionWaypointData buffer) {
        if (WaypointServerMod.runsWithClient()) {
            MapModIntegrations.onClientWaypointDownload(
                    ClientWaypointSyncEvent.listReplaced(buffer.dimensionName(), buffer.waypointLists().get(0)), this);
            return;
        }
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
        this.synchronizationTracker.clearList(dimensionName, newList.name());
        updateWaypointManagerView(dimensionName, dimensionListChanged);
        MapModIntegrations.onClientWaypointDownload(ClientWaypointSyncEvent.listReplaced(dimensionName, newList), this);
    }

    private void onDimensionWaypoint(DimensionWaypointData buffer) {
        if (WaypointServerMod.runsWithClient()) {
            MapModIntegrations.onClientWaypointDownload(
                    ClientWaypointSyncEvent.dimensionReplaced(buffer.dimensionName(), buffer.waypointLists()), this);
            return;
        }
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
        this.synchronizationTracker.clearDimension(dimensionName);
        updateWaypointManagerView(dimensionName, dimensionListChanged && fileManager != null);
        MapModIntegrations.onClientWaypointDownload(
                ClientWaypointSyncEvent.dimensionReplaced(dimensionName, currentLists),
                this
        );
    }

    private void onWorldWaypoint(List<DimensionWaypointData> dimensions) {
        if (WaypointServerMod.runsWithClient()) {
            MapModIntegrations.onClientWaypointDownload(ClientWaypointSyncEvent.worldReplaced(), this);
            return;
        }
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
        this.collections.markSnapshotApplied(ClientWaypointSource.SHARED);
        this.synchronizationTracker.clearAll();
        WaypointManagerScreen.updateAllWidgets();
        MapModIntegrations.onClientWaypointDownload(ClientWaypointSyncEvent.worldReplaced(), this);
    }

    private void onWaypointDownload(DimensionWaypointData buffer) {
        String dimensionName = buffer.dimensionName();
        WaypointList snapshot = buffer.waypointLists().get(0);
        SimpleWaypoint waypoint = snapshot.simpleWaypoints().get(0);
        if (!WaypointServerMod.runsWithClient()) {
            WaypointFileManager manager = this.getWaypointFileManager(dimensionName);
            boolean dimensionListChanged = manager == null;
            WaypointList cachedList = manager == null ? null : manager.getWaypointListByName(snapshot.name());
            // One waypoint cannot certify the revision of the rest of its list.
            if (cachedList == null || cachedList.getSyncNum() != snapshot.getSyncNum()) {
                this.synchronizationTracker.markOutOfSync(dimensionName, snapshot.name());
            }
            int revision = cachedList == null ? WaypointList.SERVER_N - 1 : cachedList.getSyncNum();
            SimpleWaypoint liveWaypoint = this.addWaypointFromRemoteServer(
                    dimensionName, snapshot.name(), snapshot.displayName(), waypoint, revision);
            try {
                this.saveWaypointFile(this.getWaypointFileManager(dimensionName));
            } catch (IOException exception) {
                LOGGER.error("Failed to save downloaded waypoint in dimension {}", dimensionName, exception);
            }
            if (dimensionName.equals(currentDimensionName)) {
                if (liveWaypoint.renderId == -1) {
                    OptimizedWaypointRenderer.add(liveWaypoint);
                } else {
                    OptimizedWaypointRenderer.updateWaypoint(liveWaypoint);
                }
            }
            updateWaypointManagerView(dimensionName, dimensionListChanged);
        }
        MapModIntegrations.onClientWaypointDownload(ClientWaypointSyncEvent.waypointModified(
                dimensionName, snapshot.name(), WaypointModificationType.ADD, waypoint, waypoint.name()), this);
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
        if (buffer.scope() == WaypointScope.PRIVATE) {
            this.applyCollectionMessage(buffer);
            return;
        }
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
        if (this.synchronizationTracker.isOutOfSync(
                message.dimensionName(),
                message.listName()
        )) {
            return false;
        }
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
            this.synchronizationTracker.markOutOfSync(
                    message.dimensionName(),
                    message.listName()
            );
            return false;
        }

        return this.synchronizationTracker.shouldApplyIncremental(
                message.dimensionName(),
                message.listName(),
                currentList.getSyncNum(),
                message.syncId()
        );
    }

    public void onWaypointListUpdate(WaypointListUpdateMessage buffer) {
        if (buffer.scope() == WaypointScope.PRIVATE) {
            this.applyCollectionMessage(buffer);
            return;
        }
        if (WaypointServerMod.runsWithClient()) {
            return;
        }
        if (networkState != ClientNetworkState.SYNC_FINISHED) {
            return;
        }
        String dimensionName = buffer.dimensionName();
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        WaypointList updated = buffer.waypointList();
        boolean replacesOutOfSyncList = this.synchronizationTracker.isOutOfSync(
                dimensionName,
                buffer.previousListIdentifier()
        ) || this.synchronizationTracker.isOutOfSync(dimensionName, updated.name());
        if (fileManager == null) {
            if (!replacesOutOfSyncList) {
                return;
            }
            fileManager = this.addWaypointFileManager(dimensionName);
        }
        WaypointList previous = fileManager.getWaypointListByName(buffer.previousListIdentifier());
        WaypointList current = previous == null
                ? fileManager.getWaypointListByName(updated.name())
                : previous;
        if (!replacesOutOfSyncList
                && current != null
                && updated.getSyncNum() <= current.getSyncNum()) {
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
        this.synchronizationTracker.clearList(
                dimensionName,
                buffer.previousListIdentifier()
        );
        this.synchronizationTracker.clearList(dimensionName, updated.name());
        WaypointManagerScreen.updateWidgetsForDimensionListChange(dimensionName);
        MapModIntegrations.onClientWaypointSync(
                ClientWaypointSyncEvent.listReplaced(dimensionName, updated),
                this
        );
    }

    public void onWaypointEditResult(WaypointEditResultMessage buffer) {
        this.access.acceptResult(buffer, this.collections.generation());
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
