package _959.server_waypoint.core;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static _959.server_waypoint.util.MapUtils.getEntriesSortedByKey;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

/**
* Load and save all waypoint files in the specified directory
**/
public class WaypointFilesManagerCore {
    public static final Logger LOGGER = LoggerFactory.getLogger("waypoint_files_manager");
    protected LinkedHashMap<String, WaypointFileManager> fileManagerMap;
    protected Path waypointFilesDir;

    /**
     * initialize without waypointFilesDir set
     * */
    public WaypointFilesManagerCore() {
        this.fileManagerMap = new LinkedHashMap<>();
    }

    public WaypointFilesManagerCore(Path waypointsDir) {
        this.waypointFilesDir = waypointsDir;
        this.fileManagerMap = new LinkedHashMap<>();
    }

    public LinkedHashMap<String, WaypointFileManager> getFileManagerMap() {
        return this.fileManagerMap;
    }

    public @Unmodifiable List<Map.Entry<String, WaypointFileManager>> getSortedMap() {
        // maintain the list order for vanilla dimensions
        return getEntriesSortedByKey(this.fileManagerMap, 3);
    }

    public @Nullable WaypointFileManager getWaypointFileManager(String dimensionName) {
        return this.fileManagerMap.get(dimensionName);
    }

    public @NotNull WaypointFileManager getOrCreateWaypointFileManager(String dimensionName) {
        WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
        return fileManager == null ? addWaypointListManager(dimensionName) : fileManager;
    }

    public void addWaypoint(String dimensionName, String listName, SimpleWaypoint waypoint, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> successAction, Consumer<@NotNull SimpleWaypoint> duplicateAction) {
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        WaypointList waypointList;
        if (fileManager == null) {
            fileManager = this.addWaypointListManager(dimensionName);
            waypointList = WaypointList.buildByServer(listName);
            fileManager.addWaypointList(waypointList);
        } else {
            waypointList = fileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                waypointList = WaypointList.buildByServer(listName);
                fileManager.addWaypointList(waypointList);
            }
        }
        SimpleWaypoint waypointFound = waypointList.getWaypointByName(waypoint.name());
        if (waypointFound == null) {
            waypointList.addByServer(waypoint);
            successAction.accept(fileManager, waypointList);
        } else {
            duplicateAction.accept(waypointFound);
        }
    }

    public void addWaypointList(String dimensionName, String listName, Consumer<WaypointFileManager> successAction, Runnable listExistsAction) {
        WaypointFileManager fileManager = this.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            fileManager = this.addWaypointListManager(dimensionName);
        }
        WaypointList foundList = fileManager.getWaypointListByName(listName);
        if (foundList == null) {
            fileManager.addWaypointList(WaypointList.buildByServer(listName));
            successAction.accept(fileManager);
        } else {
            listExistsAction.run();
        }
    }

    public void removeWaypointList(WaypointFileManager fileManager, String listName, Consumer<WaypointFileManager> successAction, Runnable listNotFoundAction, Runnable nonEmptyListAction) {
        WaypointList list = fileManager.getWaypointListByName(listName);
        if (list == null) {
            listNotFoundAction.run();
        } else if (list.isEmpty()) {
            fileManager.removeWaypointListByName(listName);
            successAction.accept(fileManager);
        } else {
            nonEmptyListAction.run();
        }
    }

    public void updateWaypointProperties(@NotNull SimpleWaypoint waypoint, String name, String initials, WaypointPos waypointPos, int rgb, int yaw, boolean global, Runnable successAction, Runnable identicalAction) {
        if (waypoint.compareProperties(name, initials, waypointPos, rgb, yaw, global)) {
            identicalAction.run();
            return;
        }
        waypoint.setName(name);
        waypoint.setInitials(initials);
        waypoint.setPos(waypointPos);
        waypoint.setRgb(rgb);
        waypoint.setYaw(yaw);
        waypoint.setGlobal(global);
        successAction.run();
    }

    public void removeWaypoint(WaypointList waypointList, SimpleWaypoint waypoint) {
        waypointList.remove(waypoint);
    }

    /**
     * Add an empty waypoint list manager to this files manager by dimension name </br>
     * */
    public WaypointFileManager addWaypointListManager(String dimensionName) {
        WaypointFileManager waypointFileManager = this.fileManagerMap.get(dimensionName);
        if (waypointFileManager != null) {
            LOGGER.warn("Duplicate dimension key: {}", dimensionName);
            return waypointFileManager;
        } else {
            WaypointFileManager fileManager = new WaypointFileManager(null, dimensionName, this.waypointFilesDir);
            this.fileManagerMap.put(dimensionName, fileManager);
            return fileManager;
        }
    }

    /**
     * Creates a new folder if waypointFilesDir does not exist </br>
     * Read all waypoint files in waypointFilesDir, clears all previously loaded waypoints
    * */
    protected void initOrReadWaypointFiles() throws IOException {
        if (this.waypointFilesDir == null) {
            LOGGER.warn("No waypoint files directory provided.");
            return;
        }
        try {
            if (!Files.exists(this.waypointFilesDir) || !Files.isDirectory(this.waypointFilesDir)) {
                Files.createDirectories(this.waypointFilesDir);
                WaypointServerCore.LOGGER.info("Created waypoints directory at: {}", this.waypointFilesDir);
            }
        } catch (IOException e) {
            WaypointServerCore.LOGGER.error("Failed to initialize waypoints directory");
            throw e;
        }

        this.fileManagerMap.clear();
        // maintain the list order for vanilla dimensions
        this.fileManagerMap.put(MINECRAFT_OVERWORLD, null);
        this.fileManagerMap.put(MINECRAFT_THE_NETHER, null);
        this.fileManagerMap.put(MINECRAFT_THE_END, null);

        List<Pair<String, WaypointFileManager>> fileManagers = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(this.waypointFilesDir)) {
            for (Path path : entries) {
                if (path.toFile().isDirectory()) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                // test for old version file name
                if (fileName.startsWith("dim%")) {
                    fileName = convertToNewFileName(fileName);
                    Files.move(path, path.resolveSibling(fileName));
                    WaypointServerCore.LOGGER.info("Old file moved to {}", fileName);
                } else if (isFileNameInvalid(fileName)) {
                    WaypointServerCore.LOGGER.error("Invalid dimension file name {}, skip", fileName);
                    continue;
                }
                // test for txt format file
                boolean isTxt = false;
                if (fileName.endsWith(".txt")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                    Files.move(path, path.resolveSibling(fileName + ".json"));
                    isTxt = true;
                } else if (fileName.endsWith(".json")) {
                    // using json from 2.8.3
                    fileName = fileName.substring(0, fileName.length() - 5);
                } else {
                    continue;
                }
                WaypointFileManager fileManager = new WaypointFileManager(fileName, null, this.waypointFilesDir);
                try {
                    if (isTxt) {
                        // convert to json format
                        fileManager.readDimensionFromTxt();
                        fileManager.saveDimension();
                    } else {
                        fileManager.readDimension();
                    }
                    fileManagers.add(new Pair<>(fileManager.getDimensionName(), fileManager));
                } catch (IOException e) {
                    WaypointServerCore.LOGGER.error("Failed to load dimension file", e);
                    throw e;
                }
            }
        }
        // sort by dimension names to get rid of random file reading order
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
            case "0" -> "minecraft$overworld.json";
            case "1" -> "minecraft$the_end.json";
            case "-1" -> "minecraft$the_nether.json";
            default -> fileName + ".json";
        };
    }

    /**
     * save all waypoint files
     */
    public void saveAllWaypointFiles() {
        for (WaypointFileManager fileManager : this.fileManagerMap.values()) {
            try {
                if (fileManager != null) {
                    fileManager.saveDimension();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to save dimension file {}", fileManager.getDimensionFile());
            }
        }
    }

    /**
    * Change the directory of waypoint files and load all waypoint files
    * */
    public void changeWaypointFilesDir(Path newPath) {
        if (Objects.equals(this.waypointFilesDir, newPath)) {
            return;
        }
        this.waypointFilesDir = newPath;
        try {
            initOrReadWaypointFiles();
        } catch (IOException e) {
            WaypointServerCore.LOGGER.error("Failed to load waypoints file from {}: {}", newPath, e);
            throw new RuntimeException(e);
        }
    }
}
