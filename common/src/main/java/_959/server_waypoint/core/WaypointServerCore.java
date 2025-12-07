package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.WaypointFilesDirectoryHelper.asDedicatedServer;

/**
 * Serverside waypoint manager used by dedicated or integrated server
 * */
public abstract class WaypointServerCore extends WaypointFilesManagerCore {
    public static WaypointServerCore INSTANCE;
    private static int worldId;
    public static Config CONFIG = new Config();
    public static final String GROUP_ID = "server_waypoint";
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");
    private static final String CONFIG_FILE_NAME = "config.json";
    private final Path configDir;
    private final byte[] DEFAULT_CONFIG;
    private final LanguageFilesManager languageFilesManager;

    /**
     * constructor for dedicated server </br>
     * integrated server can also this but must call {@link _959.server_waypoint.core.WaypointFilesManagerCore#changeWaypointFilesDir(Path) changeWaypointFilesDir}
     * before loading waypoint files
     */
    public WaypointServerCore(Path configDir) {
        super(asDedicatedServer(configDir));
        this.configDir = configDir;
        this.languageFilesManager = new LanguageFilesManager(configDir);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        this.DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes();
        addAdventureTranslator();
        INSTANCE = this;
    }

    public abstract boolean isDimensionKeyValid(String dimString);

    /**
     * Can only be called after Minecraft server initialized.
     */
    @SuppressWarnings("unused")
    public void removeInvalidDimensions() {
        for (String fileName : this.fileManagerMap.keySet()) {
            if (this.isDimensionKeyValid(fileName)) {
                fileManagerMap.remove(fileName);
            }
        }
    }

    @Nullable
    public WorldWaypointBuffer toWorldWaypointBuffer() {
        List<DimensionWaypointBuffer> dimensionWaypointBuffers = new ArrayList<>();

        for(WaypointFileManager fileManager : this.getFileManagerMap().values()) {
            if (fileManager != null && !fileManager.hasNoWaypoints()) {
                dimensionWaypointBuffers.add(fileManager.toDimensionWaypoint());
            }
        }

        if (dimensionWaypointBuffers.isEmpty()) {
            return null;
        } else {
            return new WorldWaypointBuffer(dimensionWaypointBuffers);
        }
    }

    public void loadConfig(FileReader reader) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        CONFIG = gson.fromJson(reader, Config.class);
        LOGGER.info("Loaded config {}", CONFIG);
    }

    private void initOrReadConfigFile(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        try {
            if (Files.exists(configFile) && Files.isRegularFile(configFile)) {
                this.loadConfig(new FileReader(configFile.toFile()));
            } else {
                Files.createFile(configFile);
                Files.write(configFile, this.DEFAULT_CONFIG);
                LOGGER.info("Created config file at: {}", configFile);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read config file, use default config instead", e);
        }
    }

    private void saveConfigFile(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        try {
            if (!Files.exists(configFile) || !Files.isRegularFile(configFile)) {
                Files.createFile(configFile);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(configFile, gson.toJson(CONFIG).getBytes());
            LOGGER.info("Saved config file: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
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
        this.initConfigDir(this.configDir);
        this.initOrReadConfigFile(this.configDir);;
        this.initLanguageManager();
        List<String> languages = getExternalLoadedLanguages();
        String log = String.join(", ", languages);
        LOGGER.info("Loaded {} languages: {}", languages.size(), log);
    }

    /**
     * calls saveAllFiles first then free all loaded waypoint files and external language files <br>
     * */
    public void freeAllLoadedFiles() {
        saveAllFiles();
        this.fileManagerMap.clear();
        this.languageFilesManager.unloadAllExternalLanguages();
    }

    public void reload() {
        initOrReadConfigFile(this.configDir);
        this.languageFilesManager.reloadExternalLanguages();
    }

    @SuppressWarnings("unused")
    public void reloadWaypointFiles() {
        saveAllFiles();
        try {
            initOrReadWaypointFiles();
        } catch (IOException e) {
            LOGGER.error("Failed to load waypoints file", e);
            throw new RuntimeException(e);
        }
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
