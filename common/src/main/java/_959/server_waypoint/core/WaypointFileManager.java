package _959.server_waypoint.core;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
        this.dimensionFilePath = waypointsDir.resolve(fileName + ".json");
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

    public void readDimensionFromTxt() throws IOException {
        this.readFromTxtFile(this.dimensionFilePath);
    }

    public void saveDimension() throws IOException {
        this.writeToFile(this.dimensionFilePath);
    }

    private void readFromTxtFile(Path filePath) throws IOException {
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
        WaypointServerCore.LOGGER.info("Loaded {} lists and {} waypoints from old txt file: {}", this.waypointListMap.size(), waypointsNumber, filePath);
    }

    private void readFromFile(Path filePath) throws IOException {
        ArrayList<WaypointList> waypointLists;
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            Type listType = new TypeToken<ArrayList<WaypointList>>() {}.getType();
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(WaypointPos.class, new WaypointPos.WaypointPosAdapter())
                    .excludeFieldsWithoutExposeAnnotation()
                    .create();
            waypointLists = gson.fromJson(reader, listType);
        }
        int waypointsNumber = 0;
        for (WaypointList waypointList : waypointLists) {
            this.addWaypointList(waypointList);
            waypointsNumber += waypointList.size();
        }
        WaypointServerCore.LOGGER.info("Loaded {} lists and {} waypoints from file: {}", this.waypointListMap.size(), waypointsNumber, filePath);
    }

    private void writeToFile(Path filePath) throws IOException {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            Collection<WaypointList> waypointLists = this.waypointListMap.values();
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(WaypointPos.class, new WaypointPos.WaypointPosAdapter())
                    .excludeFieldsWithoutExposeAnnotation()
                    .create();
            gson.toJson(waypointLists, writer);
            WaypointServerCore.LOGGER.info("Saved {} waypoint lists to file: {}", this.waypointListMap.size(), filePath);
        }
    }
}
