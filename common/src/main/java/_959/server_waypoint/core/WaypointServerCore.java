package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.URISyntaxException;
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
    private static int worldId;
    private Path waypointsDir;
    private Path editionFile;
    private final Path configDir;
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG;
    private LinkedHashMap<String, WaypointFileManager> fileManagerMap;
    private Translator translator;

    public WaypointServerCore(Path configDir) {
        this.DEFAULT_CONFIG = this.gson.toJson(CONFIG).getBytes();
        this.configDir = configDir;
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
            if (fileManager != null && !fileManager.isEmpty()) {
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
    }

    private void initEditionFile(Path configDir) throws IOException {
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

    private void initConfigFile(Path configDir) {
        Path configFile = configDir.resolve("config.json");

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

    private void initWaypointsFile(Path configDir) throws IOException {
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
                    this.fileManagerMap.put(fileManager.getDimensionName(), fileManager);
                } catch (IOException e) {
                    LOGGER.error("Failed to load dimension file", e);
                    throw e;
                }
            }
        }
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

    private void initLanguageManager(Path configDir) {
        try {
            new LanguageFilesManager(configDir);
            Set<String> languages = getLoadedLanguages();
            String log = String.join(".",  languages.toArray(new String[0]));
            LOGGER.info("Loaded {} languages: {}", languages.size(), log);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void initAdventureTranslator() {
        this.translator = new AdventureTranslator();
        GlobalTranslator.translator().addSource(this.translator);
    }

    public void initServer() throws IOException {
        this.initConfigDir(this.configDir);
        this.fileManagerMap = new LinkedHashMap<>();
        this.fileManagerMap.put(MINECRAFT_OVERWORLD, null);
        this.fileManagerMap.put(MINECRAFT_THE_NETHER, null);
        this.fileManagerMap.put(MINECRAFT_THE_END, null);
        this.initConfigFile(this.configDir);
        this.initEditionFile(this.configDir);
        this.initWaypointsFile(this.configDir);
        this.initLanguageManager(this.configDir);
        this.initAdventureTranslator();
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
        try {
            Path xaeromapFile = saveDir.resolve("xaeromap.txt");
            if (Files.exists(xaeromapFile) && Files.isRegularFile(xaeromapFile)) {
                //read xaeromap.txt and get the id
                String id = Files.readString(xaeromapFile);
                if (id.startsWith("id:")) {
                    worldId = Integer.parseInt(id.split(":")[1]);
                } else {
                    LOGGER.error("Invalid xaeromap.txt file, id not found");
                }
            } else {
                try {
                    int id = (new Random()).nextInt();
                    String idString = "id:" + id;
                    Files.writeString(xaeromapFile, idString);
                    worldId = id;
                } catch (Exception e) {
                    LOGGER.error("Failed to create xaeromap.txt: ", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get world ID: ", e);
        }
    }

    public static int getWorldId() {
        return worldId;
    }

}
