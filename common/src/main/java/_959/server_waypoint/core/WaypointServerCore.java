package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.collection.PrivateWaypointStore;
import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.navigation.StoredNavigationSession;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogStore;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asDedicatedServer;

/**
 * Serverside waypoint manager used by a dedicated or integrated server
 * */
public abstract class WaypointServerCore extends WaypointFilesManagerCore {
    public static volatile WaypointServerCore INSTANCE;
    private static volatile int worldId;
    public static volatile Config CONFIG = new Config();
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");
    private static final String CONFIG_FILE_NAME = "config.json";
    private final Path configDir;
    private final byte[] DEFAULT_CONFIG;
    private final LanguageFilesManager languageFilesManager;
    private final ReentrantLock configIoLock = new ReentrantLock(true);
    private volatile boolean resourcesLoaded;
    private final ReentrantReadWriteLock privateStorageLifecycle = new ReentrantReadWriteLock(true);
    private PrivateWaypointStore privateWaypoints;
    private Path privateWaypointRoot;
    private volatile RemoteCatalogStore remoteCatalogStore =
            RemoteCatalogStore.empty();

    /** Read-only service attachment; remote catalogs never enter the local waypoint file map. */
    public void setRemoteCatalogStore(RemoteCatalogStore store) {
        remoteCatalogStore = Objects.requireNonNull(store);
    }
    public RemoteCatalogStore remoteCatalogStore() { return remoteCatalogStore; }
    public Path configDirectory() { return configDir; }

    /** Validates a newly selected icon against this server, without requiring client resources. */
    public final boolean isWaypointIconValid(@Nullable NamespacedId icon) {
        if (icon == null) {
            return true;
        }
        if (icon.toString().length() > WaypointIconPolicy.MAX_LENGTH) {
            return false;
        }
        return icon.namespace().equals("voxelmap")
                ? WaypointIconPolicy.isKnownVoxelMapIcon(icon)
                : this.isRegisteredIconItem(icon);
    }

    /** Platforms accept only registered, non-air items. No registry means no accepted item IDs. */
    protected boolean isRegisteredIconItem(NamespacedId icon) {
        return false;
    }

    /**
     * constructor for a dedicated server </br>
     * integrated server can also this but must call {@link _959.server_waypoint.core.WaypointFilesManagerCore#changeWaypointFilesDir(Path) changeWaypointFilesDir}
     * before loading waypoint files
     */
    public WaypointServerCore(Path configDir) {
        super(asDedicatedServer(configDir));
        this.configDir = configDir;
        this.privateWaypointRoot = configDir.resolve("private-waypoints");
        this.privateWaypoints = new PrivateWaypointStore(this.privateWaypointRoot, () -> CONFIG.privateWaypoints());
        this.languageFilesManager = new LanguageFilesManager(configDir);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        this.DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes(StandardCharsets.UTF_8);
        addAdventureTranslator();
        INSTANCE = this;
    }

    /** Borrows one collection for the complete operation, including its persistence outcome. */
    public <T> T withCollection(WaypointCollectionKey key, PrivateWaypointStore.Operation<T> operation) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        if (key.scope() == WaypointScope.SHARED) return operation.apply(this);
        this.privateStorageLifecycle.readLock().lock();
        try {
            if (this.privateWaypoints == null) throw new IllegalStateException("Private storage is not loaded");
            return this.privateWaypoints.withOwner(key.ownerUuid(), operation);
        } finally {
            this.privateStorageLifecycle.readLock().unlock();
        }
    }

    public Optional<NavigationTarget> resolveNavigationTarget(UUID playerUuid, StoredNavigationSession stored) throws IOException {
        if (!stored.collectionKey().accessibleBy(playerUuid)) return Optional.empty();
        return this.withCollection(stored.collectionKey(), files -> stored.resolve(playerUuid, key -> files));
    }

    public void releasePrivateWaypoints(UUID playerUuid) {
        this.privateStorageLifecycle.readLock().lock();
        try {
            if (this.privateWaypoints != null) this.privateWaypoints.evict(playerUuid);
        } catch (IOException exception) {
            LOGGER.error("Failed to release private waypoints for {}; retaining loaded data", playerUuid, exception);
        } finally {
            this.privateStorageLifecycle.readLock().unlock();
        }
    }

    public void useIntegratedPrivateWaypoints(Path saveRoot) throws IOException {
        this.configurePrivateWaypointStorage(saveRoot.resolve("server_waypoint").resolve("private-waypoints"));
    }

    public void useDedicatedPrivateWaypoints() throws IOException {
        this.configurePrivateWaypointStorage(this.configDir.resolve("private-waypoints"));
    }

    private void configurePrivateWaypointStorage(Path root) throws IOException {
        if (this.privateStorageLifecycle.getReadHoldCount() != 0) {
            throw new IllegalStateException("Cannot rebind private storage during an owner operation");
        }
        this.privateStorageLifecycle.writeLock().lock();
        try {
            if (this.privateWaypoints != null && root.equals(this.privateWaypointRoot)) return;
            if (this.privateWaypoints != null) this.privateWaypoints.close();
            this.privateWaypointRoot = root;
            this.privateWaypoints = new PrivateWaypointStore(root, () -> CONFIG.privateWaypoints());
        } finally {
            this.privateStorageLifecycle.writeLock().unlock();
        }
    }

    private void flushPrivateWaypoints(boolean close) {
        if (this.privateStorageLifecycle.getReadHoldCount() != 0) {
            throw new IllegalStateException("Cannot flush private storage during an owner operation");
        }
        this.privateStorageLifecycle.writeLock().lock();
        try {
            if (this.privateWaypoints == null) return;
            if (close) {
                this.privateWaypoints.close();
                this.privateWaypoints = null;
            } else {
                this.privateWaypoints.flush();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save private waypoints", exception);
        } finally {
            this.privateStorageLifecycle.writeLock().unlock();
        }
    }

    @Nullable
    public WaypointData toWorldWaypointData() {
        return this.readLifecycle(() -> {
            Collection<WaypointFileManager> fileManagers = this.fileManagerMap.values();
            List<DimensionWaypointData> dimensions = new ArrayList<>(fileManagers.size());
            for (WaypointFileManager fileManager : fileManagers) {
                if (fileManager != null && !fileManager.hasNoWaypoints()) {
                    dimensions.add(fileManager.toDimensionWaypointData());
                }
            }

            if (dimensions.isEmpty()) {
                return null;
            }
            return WaypointData.world(dimensions);
        });
    }

    public void loadConfig(Reader reader) {
        this.configIoLock.lock();
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Config loadedConfig = gson.fromJson(reader, Config.class);
            if (loadedConfig == null) {
                loadedConfig = new Config();
            }
            try {
                loadedConfig.privateWaypoints();
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Invalid private waypoint configuration", exception);
            }
            CONFIG = loadedConfig;
            LOGGER.info("Loaded config {}", loadedConfig);
        } finally {
            this.configIoLock.unlock();
        }
    }

    private void initOrReadConfigFile(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        this.configIoLock.lock();
        try {
            if (Files.exists(configFile) && Files.isRegularFile(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    this.loadConfig(reader);
                }
            } else {
                this.writeFileAtomically(configFile, this.DEFAULT_CONFIG);
                LOGGER.info("Created config file at: {}", configFile);
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to read config file, keep the current config instead", e);
        } finally {
            this.configIoLock.unlock();
        }
    }

    private void saveConfigFile(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        this.configIoLock.lock();
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            byte[] configJson = gson.toJson(CONFIG).getBytes(StandardCharsets.UTF_8);
            this.writeFileAtomically(configFile, configJson);
            LOGGER.info("Saved config file: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
        } finally {
            this.configIoLock.unlock();
        }
    }

    private void writeFileAtomically(Path file, byte[] contents) throws IOException {
        Path targetFile = file.toAbsolutePath();
        Path parent = targetFile.getParent();
        if (parent == null) {
            throw new IOException("Config file has no parent directory: " + file);
        }
        Files.createDirectories(parent);
        Path tempFile = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            Files.write(tempFile, contents);
            try {
                Files.move(
                        tempFile,
                        targetFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void initConfigDir(Path configDir) throws IOException {
        if (!Files.isDirectory(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                LOGGER.error("Failed to initialize config directory");
                throw e;
            }
        }
    }

    private void initLanguageManager() {
        try {
            this.languageFilesManager.initLanguageManager();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize language manager");
            throw new RuntimeException(e);
        }
    }

    private void addAdventureTranslator() {
        Translator translator = new AdventureTranslator();
        GlobalTranslator.translator().addSource(translator);
    }

    /**
     * only initialize config file and language files, should only call once
     * */
    public void initConfigAndLanguageResource() throws IOException {
        this.resourcesLoaded = false;
        this.configurePrivateWaypointStorage(this.privateWaypointRoot);
        this.initConfigDir(this.configDir);
        this.initOrReadConfigFile(this.configDir);
        this.initLanguageManager();
        this.resourcesLoaded = true;
        List<String> languages = getExternalLoadedLanguages();
        String log = String.join(", ", languages);
        LOGGER.info("Loaded {} languages: {}", languages.size(), log);
    }

    /** Saves and closes owner stores before taking Shared lifecycle locks, then frees Shared resources. */
    public void freeAllLoadedFiles() {
        this.flushPrivateWaypoints(true);
        this.withLifecycleWriteLock(() -> {
            this.resourcesLoaded = false;
            this.saveSharedFiles();
            this.clearWaypointFileManagers();
            this.languageFilesManager.unloadAllExternalLanguages();
        });
    }

    public void reload() {
        this.withLifecycleWriteLock(() -> {
            if (!this.resourcesLoaded) {
                LOGGER.warn("Ignoring reload because server resources are not loaded");
                return;
            }
            initOrReadConfigFile(this.configDir);
            this.languageFilesManager.reloadExternalLanguages();
        });
    }

    @SuppressWarnings("unused")
    public void reloadWaypointFiles() {
        if (!this.resourcesLoaded) {
            LOGGER.warn("Ignoring waypoint reload because server resources are not loaded");
            return;
        }
        this.flushPrivateWaypoints(false);
        this.withLifecycleWriteLock(() -> {
            if (!this.resourcesLoaded) {
                LOGGER.warn("Ignoring waypoint reload because server resources are not loaded");
                return;
            }
            this.saveSharedFiles();
            try {
                initOrReadWaypointFiles();
            } catch (IOException e) {
                LOGGER.error("Failed to load waypoints file", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * save all config file and waypoint files
     */
    public void saveAllFiles() {
        this.flushPrivateWaypoints(false);
        this.saveSharedFiles();
    }

    private void saveSharedFiles() {
        saveAllWaypointFiles();
        saveConfigFile(this.configDir);
    }

    public void initXearoWorldId(Path saveDir) {
        Path xaeromapFile = saveDir.resolve("xaeromap.txt");
        try {
            if (Files.exists(xaeromapFile) && Files.isRegularFile(xaeromapFile)) {
                //read xaeromap.txt and get the id
                String idString = Files.readString(xaeromapFile).trim();
                if (idString.startsWith("id:")) {
                    worldId = Integer.parseInt(idString.split(":")[1]);
                    return;
                } else {
                    LOGGER.error("Invalid xaeromap.txt file format, cannot read id, creating a new one");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read xaeromap file. creating a new one", e);
        }
        try {
            int id = (new Random()).nextInt();
            String idString = "id:" + id;
            Files.writeString(xaeromapFile, idString);
            worldId = id;
        } catch (Exception e) {
            CONFIG.Features().sendXaerosWorldId(false);
            LOGGER.error("Cannot enable sendXaerosWorldId: failed to create xaeromap.txt: ", e);
        }
    }

    protected final void setXaeroWorldId(int id) {
        worldId = id;
    }

    public static int getWorldId() {
        if (CONFIG.Features().sendXaerosWorldId()) {
            return worldId;
        } else {
            throw new IllegalStateException("Should not call this when sendXaerosWorldId is disabled.");
        }
    }
}
