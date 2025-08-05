package _959.server_waypoint.core;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.translation.LanguageFilesManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WaypointServerCore {
    public static int EDITION = 0;
    public static Config CONFIG = new Config();
    public static final String GROUP_ID = "server_waypoint";
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");
    private static int worldId;
    private final Predicate<String> fileNameVerifier;
    private Path waypointsDir;
    private Path editionFile;
    private final Path configDir;
    private final Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG;
    private LinkedHashMap<String, WaypointFileManager> fileManagerMap;

    public WaypointServerCore(Path configDir, Predicate<String> verifyFileName) {
        this.DEFAULT_CONFIG = this.gson.toJson(CONFIG).getBytes();
        this.configDir = configDir;
        this.fileNameVerifier = verifyFileName;
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

    private void initWaypointsFile(Path configDir, Predicate<String> isFileNameValid) throws IOException {
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
                String fileName = path.getFileName().toString().replace(".txt", "");
                if (!path.toFile().isDirectory() && fileName.startsWith("dim%")) {
                    if (!isFileNameValid.test(fileName)) {
                        LOGGER.error("Invalid dimension file name {}, skip", fileName);
                    } else {
                        WaypointFileManager dimLists = new WaypointFileManager(fileName, this.waypointsDir);

                        try {
                            dimLists.readDimension();
                            this.fileManagerMap.put(fileName, dimLists);
                        } catch (IOException e) {
                            LOGGER.error("Failed to load dimension file", e);
                            throw e;
                        }
                    }
                }
            }
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
        };
    }

    private void initLanguageManager(Path configDir) {
        try {
            new LanguageFilesManager(configDir);
            LOGGER.info("Loaded language files.");
            LOGGER.info("{}", LanguageFilesManager.getTranslation("a"));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void initServer() throws IOException {
        this.initConfigDir(this.configDir);
        this.fileManagerMap = new LinkedHashMap<>();
        this.fileManagerMap.put("dim%0", null);
        this.fileManagerMap.put("dim%-1", null);
        this.fileManagerMap.put("dim%1", null);
        this.initConfigFile(this.configDir);
        this.initEditionFile(this.configDir);
        this.initWaypointsFile(this.configDir, this.fileNameVerifier);
        this.initLanguageManager(this.configDir);
    }

    public Map<String, WaypointFileManager> getFileManagerMap() {
        return this.fileManagerMap;
    }

    public @Nullable WaypointFileManager getWaypointFileManager(String dimensionKey) {
        return this.fileManagerMap.get(dimensionKey);
    }

    public WaypointFileManager addWaypointFileManager(String dimensionKey) {
        WaypointFileManager waypointFileManager = this.fileManagerMap.get(dimensionKey);
        if (waypointFileManager != null) {
            LOGGER.warn("Duplicate dimension key: {}", dimensionKey);
            return waypointFileManager;
        } else {
            WaypointFileManager fileManager = new WaypointFileManager(dimensionKey, this.waypointsDir);
            this.fileManagerMap.put(dimensionKey, fileManager);
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
                    LOGGER.error("Failed to create xaeromap.txt: " + e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get world ID: " + e);
        }
    }

    public static int getWorldId() {
        return worldId;
    }

}
