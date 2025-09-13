package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import _959.server_waypoint.util.Pair;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.translation.LanguageFilesManager.getLoadedLanguages;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public abstract class WaypointServerCore {
    public static WaypointServerCore INSTANCE;
    public static int EDITION = 0;
    public static Config CONFIG = new Config();
    public static final String GROUP_ID = "server_waypoint";
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");
    private static final String CONFIG_FILE_NAME = "config.json";
    private static int worldId;
    private Path waypointsDir;
    private Path editionFile;
    private final Path configDir;
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG;
    private final LinkedHashMap<String, WaypointFileManager> fileManagerMap;
    private final LanguageFilesManager languageFilesManager;

    public WaypointServerCore(Path configDir) {
        this.configDir = configDir;
        this.fileManagerMap = new LinkedHashMap<>();
        this.languageFilesManager = new LanguageFilesManager(configDir);
        this.DEFAULT_CONFIG = this.gson.toJson(CONFIG).getBytes();
        addAdventureTranslator();
        INSTANCE = this;
    }

    public abstract boolean isDimensionKeyValid(String dimString);

    /** Can only be called after Minecraft server initialized.
     */
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
            return new WorldWaypointBuffer(dimensionWaypointBuffers, EDITION);
        }
    }

    public void loadConfig(FileReader reader) {
        CONFIG = this.gson.fromJson(reader, Config.class);
        LOGGER.info("Loaded config {}", CONFIG);
    }

    private void initOrReadEditionFile(Path configDir) throws IOException {
        this.editionFile = configDir.resolve("EDITION");

        try {
            if (Files.exists(this.editionFile) && Files.isRegularFile(this.editionFile)) {
                try (DataInputStream in = new DataInputStream(new FileInputStream(this.editionFile.toFile()))) {
                    EDITION = in.readInt();
                    LOGGER.info("Read EDITION from file: {}", EDITION);
                }
            } else {
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
                    out.writeInt(EDITION);
                    LOGGER.info("Created EDITION file with edition: {}", EDITION);
                }
            }

        } catch (IOException e) {
            LOGGER.error("Failed to initialize server_waypoints directory or EDITION file", e);
            throw e;
        }
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
            Files.write(configFile, this.gson.toJson(CONFIG).getBytes());
            LOGGER.info("Saved config file: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
        }
    }

    private void initOrReadWaypointsFile(Path configDir) throws IOException {
        Path waypointsFolder = configDir.resolve("waypoints");

        try {
            if (!Files.exists(waypointsFolder) || !Files.isDirectory(waypointsFolder)) {
                Files.createDirectories(waypointsFolder);
                LOGGER.info("Created server_waypoints/waypoints directory at: {}", waypointsFolder);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize server_waypoints directory");
            throw e;
        }

        this.waypointsDir = waypointsFolder;
        List<Pair<String, WaypointFileManager>> fileManagers = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(waypointsFolder)) {
            for (Path path : entries) {
                if (path.toFile().isDirectory()) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(".txt")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                } else {
                    continue;
                }
                if (fileName.startsWith("dim%")) {
                    fileName = convertToNewFileName(fileName);
                    Files.move(path, path.resolveSibling(fileName + ".txt"));
                    LOGGER.info("Old file moved to {}", fileName);
                } else if (isFileNameInvalid(fileName)) {
                    LOGGER.error("Invalid dimension file name {}, skip", fileName);
                    continue;
                }
                WaypointFileManager fileManager = new WaypointFileManager(fileName, null, this.waypointsDir);
                try {
                    fileManager.readDimension();
                    fileManagers.add(new Pair<>(fileManager.getDimensionName(), fileManager));
                } catch (IOException e) {
                    LOGGER.error("Failed to load dimension file", e);
                    throw e;
                }
            }
        }
        fileManagers.sort(Comparator.comparing(Pair::left));
        for  (Pair<String, WaypointFileManager> pair : fileManagers) {
            fileManagerMap.put(pair.left(), pair.right());
        }
        fileManagers.clear();
    }

    private boolean isFileNameInvalid(String fileName) {
        return fileName.split("\\$").length != 2;
    }

    private String convertToNewFileName(String fileName) {
        fileName = fileName.substring(4);
        return switch (fileName) {
            case "0" -> "minecraft$overworld";
            case "1" -> "minecraft$the_end";
            case "-1" -> "minecraft$the_nether";
            default -> fileName;
        };
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

    public void initServer() throws IOException {
        this.initConfigDir(this.configDir);
        // maintain the list order for vanilla dimensions
        this.fileManagerMap.put(MINECRAFT_OVERWORLD, null);
        this.fileManagerMap.put(MINECRAFT_THE_NETHER, null);
        this.fileManagerMap.put(MINECRAFT_THE_END, null);
        this.initOrReadConfigFile(this.configDir);
        this.initOrReadEditionFile(this.configDir);
        this.initOrReadWaypointsFile(this.configDir);
        this.initLanguageManager();
        Set<String> languages = getLoadedLanguages();
        String log = String.join(", ",  languages.toArray(new String[0]));
        LOGGER.info("Loaded {} languages: {}", languages.size(), log);
    }

    /**
     * called saveAllFiles first then free all loaded waypoint files and language files <br>
     * must call initServer to load all resources back
     * */
    public void freeAllLoadedFiles() {
        saveAllFiles();
        this.fileManagerMap.clear();
        this.languageFilesManager.unloadAllLanguages();
    }

    public void reload() {
        initOrReadConfigFile(this.configDir);
        this.languageFilesManager.loadAllExternalLanguageFiles();
    }

    /**
     * save all waypoint files and EDITION file
     */
    public void saveAllFiles() {
        for (WaypointFileManager fileManager : this.fileManagerMap.values()) {
            try {
                if (fileManager != null) {
                    fileManager.saveDimension();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to save dimension file {}", fileManager.getDimensionFile());
            }
        }
        try {
            saveEdition();
        } catch (IOException e) {
            return;
        }
        saveConfigFile(this.configDir);
    }

    public Map<String, WaypointFileManager> getFileManagerMap() {
        return this.fileManagerMap;
    }

    public @Nullable WaypointFileManager getWaypointFileManager(String dimString) {
        return this.fileManagerMap.get(dimString);
    }

    public WaypointFileManager addWaypointFileManager(String dimString) {
        WaypointFileManager waypointFileManager = this.fileManagerMap.get(dimString);
        if (waypointFileManager != null) {
            LOGGER.warn("Duplicate dimension key: {}", dimString);
            return waypointFileManager;
        } else {
            WaypointFileManager fileManager = new WaypointFileManager(null, dimString, this.waypointsDir);
            this.fileManagerMap.put(dimString, fileManager);
            return fileManager;
        }
    }

    public void saveEdition() throws IOException {
        try {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
                out.writeInt(EDITION);
                LOGGER.info("Saved edition: {}", EDITION);
            }

        } catch (IOException e) {
            LOGGER.error("Failed to save edition to file, sync may not work properly.", e);
            throw e;
        }
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
