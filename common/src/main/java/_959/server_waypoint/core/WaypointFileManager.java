package _959.server_waypoint.core;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

public class WaypointFileManager {
    private final String fileName;
    private final Path dimensionFilePath;
    private final Map<String, WaypointList> waypointListMap;

    public WaypointFileManager(String fileName, Path waypointsDir) {
        this.fileName = fileName;
        this.waypointListMap = new HashMap<>();
        this.dimensionFilePath = waypointsDir.resolve(fileName + ".txt");
    }

    public String getFileName() {
        return this.fileName;
    }

    public Path getDimensionFile() {
        return this.dimensionFilePath;
    }

    public List<WaypointList> getWaypointLists() {
        return new ArrayList<>(this.waypointListMap.values());
    }

    public Map<String, WaypointList> getWaypointListMap() {
        return this.waypointListMap;
    }

    public @Nullable WaypointList getWaypointListByName(String name) {
        return this.waypointListMap.get(name);
    }

    public void addWaypointList(WaypointList waypointList) {
        this.waypointListMap.put(waypointList.name(), waypointList);
    }

    public void removeWaypointListByName(String name) {
        this.waypointListMap.remove(name);
    }

    public void readDimension() throws IOException {
        this.readFromFile(this.dimensionFilePath);
    }

    public void saveDimension() throws IOException {
        this.writeToFile(this.dimensionFilePath);
    }

    private void readFromFile(Path filePath) throws IOException {
        WaypointList currentList = null;

        for (String line : Files.readAllLines(filePath)) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (line.startsWith("#")) {
                    String name = line.substring(1).trim();
                    currentList = WaypointList.build(name);
                    this.addWaypointList(currentList);
                    WaypointServerCore.LOGGER.info("Created waypoint list: {}", name);
                } else if (currentList != null) {
                    try {
                        SimpleWaypoint waypoint = SimpleWaypoint.fromString(line);
                        currentList.add(waypoint);
                        WaypointServerCore.LOGGER.info("Added waypoint: {} to list: {}", waypoint.name(), currentList.name());
                    } catch (Exception e) {
                        WaypointServerCore.LOGGER.error("Failed to parse waypoint line: {}", line, e);
                    }
                }
            }
        }

    }

    private void writeToFile(Path filePath) throws IOException {
        List<String> lines = new ArrayList<>();

        for (Map.Entry<String, WaypointList> entry : this.waypointListMap.entrySet()) {
            String name = entry.getKey();
            WaypointList list = entry.getValue();
            lines.add("#" + name);

            for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                lines.add(waypoint.toSaveString());
            }
        }

        Files.write(filePath, lines);
        WaypointServerCore.LOGGER.info("Saved waypointList to file: {}", filePath);
    }
}
