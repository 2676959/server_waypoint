package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
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

    /**
     * constructor for a dedicated server </br>
     * integrated server can also this but must call {@link _959.server_waypoint.core.WaypointFilesManagerCore#changeWaypointFilesDir(Path) changeWaypointFilesDir}
     * before loading waypoint files
     */
    public WaypointServerCore(Path configDir) {
        super(asDedicatedServer(configDir));
        this.configDir = configDir;
        this.languageFilesManager = new LanguageFilesManager(configDir);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        this.DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes(StandardCharsets.UTF_8);
        addAdventureTranslator();
        INSTANCE = this;
    }

    @Nullable
    public WorldWaypointBuffer toWorldWaypointBuffer() {
        return this.readLifecycle(() -> {
            Collection<WaypointFileManager> fileManagers = this.fileManagerMap.values();
            List<DimensionWaypointBuffer> dimensionWaypointBuffers = new ArrayList<>(fileManagers.size());
            for (WaypointFileManager fileManager : fileManagers) {
                if (fileManager != null && !fileManager.hasNoWaypoints()) {
                    dimensionWaypointBuffers.add(fileManager.toDimensionWaypoint());
                }
            }

            if (dimensionWaypointBuffers.isEmpty()) {
                return null;
            }
            return new WorldWaypointBuffer(dimensionWaypointBuffers);
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
        this.initConfigDir(this.configDir);
        this.initOrReadConfigFile(this.configDir);
        this.initLanguageManager();
        this.resourcesLoaded = true;
        List<String> languages = getExternalLoadedLanguages();
        String log = String.join(", ", languages);
        LOGGER.info("Loaded {} languages: {}", languages.size(), log);
    }

    /**
     * calls saveAllFiles first then free all loaded waypoint files and external language files <br>
     * */
    public void freeAllLoadedFiles() {
        this.withLifecycleWriteLock(() -> {
            this.resourcesLoaded = false;
            saveAllFiles();
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
        this.withLifecycleWriteLock(() -> {
            if (!this.resourcesLoaded) {
                LOGGER.warn("Ignoring waypoint reload because server resources are not loaded");
                return;
            }
            saveAllFiles();
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
        saveAllWaypointFiles();
        saveConfigFile(this.configDir);
    }

    public void initXearoWorldId(Path saveDir) {
        Path xaeromapFile = saveDir.resolve("xaeromap.txt");
        try {
            if (Files.exists(xaeromapFile) && Files.isRegularFile(xaeromapFile)) {
                //read xaeromap.txt and get the id
                String idString = Files.readString(xaeromapFile);
                if (idString.startsWith("id:")) {
                    worldId = Integer.parseInt(idString.split(":")[1]);
                } else {
                    LOGGER.error("Invalid xaeromap.txt file format, cannot read id, creating a new one");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read xaeromap file. creating a new one", e);
            try {
                int id = (new Random()).nextInt();
                String idString = "id:" + id;
                Files.writeString(xaeromapFile, idString);
                worldId = id;
            } catch (Exception ee) {
                CONFIG.Features().sendXaerosWorldId(false);
                LOGGER.error("Cannot enable sendXaerosWorldId: failed to create xaeromap.txt: ", ee);
            }
        }
    }

    public static int getWorldId() {
        if (CONFIG.Features().sendXaerosWorldId()) {
            return worldId;
        } else {
            throw new IllegalStateException("Should not call this when sendXaerosWorldId is disabled.");
        }
    }
}
