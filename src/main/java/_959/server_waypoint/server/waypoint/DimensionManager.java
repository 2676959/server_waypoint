package _959.server_waypoint.server.waypoint;

import _959.server_waypoint.ServerWaypointFabric;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.util.SimpleWaypointHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.util.DimensionFileHelper.getFileName;

public class DimensionManager {
    public Path dimensionFilePath;
    private final RegistryKey<World> dimensionKey;
    private final Map<String, WaypointList> waypointListMap;

    public DimensionManager(RegistryKey<World> dimensionKey, Path waypointFilePath) {
        this.dimensionKey = dimensionKey;
        this.waypointListMap = new HashMap<>();
        this.dimensionFilePath = waypointFilePath.resolve(getFileName(dimensionKey) + ".txt");
    }

    public RegistryKey<World> getDimensionKey() {
        return this.dimensionKey;
    }

    public Map<String, WaypointList> getWaypointListMap() {
        return this.waypointListMap;
    }

    public DimensionWaypoint toDimensionWaypoint() {
        return new DimensionWaypoint(this.dimensionKey, this.waypointListMap.values().stream().toList());
    }

    @Nullable
    public WaypointList getWaypointListByName(String name) {
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
            if (line.isEmpty()) continue;
            
            if (line.startsWith("#")) {
                // New waypoint list
                String name = line.substring(1).trim();
                currentList = WaypointList.build(name);
                addWaypointList(currentList);
                ServerWaypointFabric.LOGGER.info("Created waypoint list: {}", name);
            } else if (currentList != null) {
                // Waypoint line
                try {
                    SimpleWaypoint waypoint = SimpleWaypointHelper.stringToSimpleWaypoint(line);
                    currentList.add(waypoint);
                    ServerWaypointFabric.LOGGER.info("Added waypoint: {} to list: {}", waypoint.name(), currentList.name());
                } catch (Exception e) {
                    ServerWaypointFabric.LOGGER.error("Failed to parse waypoint line: {}", line, e);
                }
            }
        }
    }

    private void writeToFile(Path filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        
        for (Map.Entry<String, WaypointList> entry : this.waypointListMap.entrySet()) {
            String name = entry.getKey();
            WaypointList list = entry.getValue();
            
            // Write list header
            lines.add("#" + name);
            
            // Write waypointList
            for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                try {
                    String waypointLine = SimpleWaypointHelper.simpleWaypointToString(waypoint);
                    lines.add(waypointLine);
                    ServerWaypointFabric.LOGGER.info("Wrote waypoint: {} from list: {}", waypoint.name(), name);
                } catch (Exception e) {
                    ServerWaypointFabric.LOGGER.error("Failed to write waypoint: {} from list: {}", waypoint.name(), name, e);
                }
            }
        }
        
        Files.write(filePath, lines);
        ServerWaypointFabric.LOGGER.info("Saved waypointList to file: {}", filePath);
    }
} 