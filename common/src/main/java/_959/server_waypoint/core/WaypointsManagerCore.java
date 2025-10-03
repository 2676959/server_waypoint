package _959.server_waypoint.core;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class WaypointsManagerCore<T extends WaypointListManager> {
    public static final Logger LOGGER = LoggerFactory.getLogger("waypoint_manager");
    protected final LinkedHashMap<String, T> fileManagerMap;

    protected WaypointsManagerCore() {
        this.fileManagerMap = new LinkedHashMap<>();
    }

    protected abstract T createWaypointListManager(String dimensionName);

    public Map<String, T> getFileManagerMap() {
        return this.fileManagerMap;
    }

    public @Nullable T getWaypointListManager(String dimensionName) {
        return this.fileManagerMap.get(dimensionName);
    }

    public T addWaypointListManager(String dimensionName) {
        T waypointFileManager = this.fileManagerMap.get(dimensionName);
        if (waypointFileManager != null) {
            LOGGER.warn("Duplicate dimension key: {}", dimensionName);
            return waypointFileManager;
        } else {
            T fileManager = createWaypointListManager(dimensionName);
            this.fileManagerMap.put(dimensionName, fileManager);
            return fileManager;
        }
    }
}
