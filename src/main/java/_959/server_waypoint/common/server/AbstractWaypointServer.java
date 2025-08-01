package _959.server_waypoint.common.server;

import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.common.network.waypoint.WorldWaypoint;
import _959.server_waypoint.common.server.waypoint.WaypointFileManager;
import _959.server_waypoint.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static _959.server_waypoint.common.ServerWaypointMod.LOGGER;

public abstract class AbstractWaypointServer {
    public static int EDITION = 0;
    public static Config CONFIG = new Config();
    private Path waypointsDir;
    private Path editionFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes();
    private LinkedHashMap<String, WaypointFileManager> dimensionManagerMap;

    public abstract Path getRootConfigDirectory();

    public void loadConfig(FileReader reader) {
        CONFIG = gson.fromJson(reader, Config.class);
    }

    private void initEditionFile(Path configDir) throws IOException {
        this.editionFile = configDir.resolve("EDITION");
        try {
            // Read or create EDITION file
            if (!Files.exists(this.editionFile) || !Files.isRegularFile(this.editionFile)) {
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
                    out.writeInt(EDITION);
                    LOGGER.info("Created EDITION file with edition: {}", EDITION);
                }
            } else {
                try (DataInputStream in = new DataInputStream(new FileInputStream(this.editionFile.toFile()))) {
                    EDITION = in.readInt();
                    LOGGER.info("Read EDITION from file: {}", EDITION);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize server_waypoints directory or EDITION file", e);
            throw e;
        }
    }

    private void initConfigFile(Path configDir) throws IOException {
        Path configFile = configDir.resolve("config.json");
        try {
            if (!Files.exists(configFile) || !Files.isRegularFile(configFile)) {
                Files.createFile(configFile);
                Files.write(configFile, DEFAULT_CONFIG);
                LOGGER.info("Created config file at: {}", configFile);
            } else {
                this.loadConfig(new FileReader(configFile.toFile()));
            }
        } catch (Exception e) {
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

        try (DirectoryStream<Path> itemList = Files.newDirectoryStream(waypointsFolder)) {
            for (Path path : itemList) {
                String fileName = path.getFileName().toString().replace(".txt", "");
                if (fileName.startsWith("dim%")) {
                    if (!isFileNameValid.test(fileName)) {
                        LOGGER.error("Invalid dimension file name {}, skip", fileName);
                        continue;
                    }
                    LOGGER.info("Found dimension file: {}", fileName);
                    WaypointFileManager dimLists = new WaypointFileManager(fileName, this.waypointsDir);
                    this.dimensionManagerMap.put(fileName, dimLists);
                    if (Files.exists(dimLists.dimensionFilePath)) {
                        LOGGER.info("Loading waypointList from: {}", dimLists.dimensionFilePath);
                        try {
                            dimLists.readDimension();
                        } catch (IOException e) {
                            LOGGER.error("Failed to load dimension file", e);
                            throw e;
                        }
                    }
                }
            }
        }
    }

    public abstract boolean verifyFileName(String fileName);

    public void initServer() throws IOException {
        this.dimensionManagerMap = new LinkedHashMap<>();
        this.dimensionManagerMap.put("dim%0", null);
        this.dimensionManagerMap.put("dim%-1", null);
        this.dimensionManagerMap.put("dim%1", null);

        Path configDir = this.getRootConfigDirectory();
        this.initConfigFile(configDir);
        this.initEditionFile(configDir);
        this.initWaypointsFile(configDir, this::verifyFileName);
    }

    @Nullable
    public WorldWaypoint toWorldWaypoint() {
        List<DimensionWaypoint> dimensionWaypoints = new ArrayList<>();
        for (WaypointFileManager waypointFileManager : this.dimensionManagerMap.values()) {
            if (waypointFileManager != null) {
                dimensionWaypoints.add(waypointFileManager.toDimensionWaypoint());
            }
        }
        if (dimensionWaypoints.isEmpty()) {
            return null;
        }
        return new WorldWaypoint(dimensionWaypoints);
    }

    public Map<String, WaypointFileManager> getDimensionManagerMap() {
        return this.dimensionManagerMap;
    }

    @Nullable
    public WaypointFileManager getDimensionManager(String dimensionKey) {
        return this.dimensionManagerMap.get(dimensionKey);
    }

    public WaypointFileManager addDimensionManager(String dimensionKey) {
        WaypointFileManager waypointFileManager = this.dimensionManagerMap.get(dimensionKey);
        if (waypointFileManager != null) {
            LOGGER.warn("Duplicate dimension key: {}", dimensionKey);
            return waypointFileManager;
        } else {
            WaypointFileManager newDimManager = new WaypointFileManager(dimensionKey, this.waypointsDir);
            this.dimensionManagerMap.put(dimensionKey, newDimManager);
            return newDimManager;
        }
    }


    public void saveEdition() throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
            out.writeInt(EDITION);
            LOGGER.info("Saved edition: {}", EDITION);
        } catch (IOException e) {
            LOGGER.error("Failed to save edition to file, sync may not work properly.", e);
            throw e;
        }
    }
}
