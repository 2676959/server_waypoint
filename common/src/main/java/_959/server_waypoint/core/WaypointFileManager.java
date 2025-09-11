package _959.server_waypoint.core;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
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
    private final String dimensionName;
    private final Path dimensionFilePath;
    private final Map<String, WaypointList> waypointListMap;

    public WaypointFileManager(String fileName, String dimensionName, Path waypointsDir) {
        if (fileName == null && dimensionName != null) {
            fileName = dimensionName.replace("/", "%").replace(":", "$");
        } else if (fileName != null && dimensionName == null) {
            dimensionName = fileName.replace("%", "/").replace("$", ":");
        }
        this.dimensionName = dimensionName;
        this.waypointListMap = new HashMap<>();
        this.dimensionFilePath = waypointsDir.resolve(fileName + ".txt");
    }

    @Nullable
    public DimensionWaypointBuffer toDimensionWaypoint() {
        List<WaypointList> waypointLists = new ArrayList<>();
        for (WaypointList waypointList : this.waypointListMap.values()) {
            if (!waypointList.isEmpty()) {
                waypointLists.add(waypointList);
            }
        }
        if (waypointLists.isEmpty()) {
            return null;
        }
        return new DimensionWaypointBuffer(this.dimensionName, waypointLists);
    }

    public boolean hasNoWaypoints() {
        for (WaypointList waypointList : this.waypointListMap.values()) {
            if (!waypointList.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return this.waypointListMap.isEmpty();
    }

    public String getDimensionName() {
        return this.dimensionName;
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

        int waypointsNumber = 0;
        for (String line : Files.readAllLines(filePath)) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (line.startsWith("#")) {
                    String name = line.substring(1).trim();
                    currentList = WaypointList.build(name);
                    this.addWaypointList(currentList);
                } else if (currentList != null) {
                    try {
                        SimpleWaypoint waypoint = SimpleWaypoint.fromString(line);
                        currentList.add(waypoint);
                        waypointsNumber++;
                    } catch (Exception e) {
                        WaypointServerCore.LOGGER.error("Failed to parse waypoint line: {}", line, e);
                    }
                }
            }
        }
        WaypointServerCore.LOGGER.info("Loaded {} lists and {} waypoints from file: {}", this.waypointListMap.size(), waypointsNumber, filePath);
    }

    private void writeToFile(Path filePath) throws IOException {
        List<String> lines = new ArrayList<>();

        int waypointsNumber = 0;
        for (Map.Entry<String, WaypointList> entry : this.waypointListMap.entrySet()) {
            String name = entry.getKey();
            WaypointList list = entry.getValue();
            lines.add("#" + name);

            List<SimpleWaypoint> waypointList = list.simpleWaypoints();
            for (SimpleWaypoint waypoint : waypointList) {
                lines.add(waypoint.toSaveString());
            }
            waypointsNumber += waypointList.size();
        }

        Files.write(filePath, lines);
        WaypointServerCore.LOGGER.info("Saved {} lists and {} waypoints to file: {}", waypointListMap.size(), waypointsNumber, filePath);
    }
}
