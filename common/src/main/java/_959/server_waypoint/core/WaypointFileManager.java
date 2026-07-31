package _959.server_waypoint.core;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Thread-safe owner of the waypoint lists for one dimension.
 *
 * <p>State snapshots are detached before they escape to persistence or network buffers.
 * File writes are serialized and published with an atomic replace.</p>
 */
public class WaypointFileManager {
    private final Map<String, WaypointList> waypointListMap;
    private final Path dimensionFilePath;
    private final String dimensionName;
    private final MutationAuthority mutationAuthority = new MutationAuthority();
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final ReentrantLock fileIoLock = new ReentrantLock(true);

    private WaypointFileManager(@NotNull String fileName, @NotNull String dimensionName, @NotNull Path waypointsDir) {
        this.dimensionFilePath = waypointsDir.resolve(fileName + ".json");
        this.dimensionName = dimensionName;
        this.waypointListMap = new HashMap<>();
    }

    public DimensionWaypointBuffer toDimensionWaypoint() {
        return new DimensionWaypointBuffer(this.dimensionName, this.snapshotWaypointLists());
    }

    public Path getDimensionFile() {
        return this.dimensionFilePath;
    }

    void readDimension() throws IOException {
        this.fileIoLock.lock();
        try {
            this.replaceWaypointLists(this.readFromFile(this.dimensionFilePath));
        } finally {
            this.fileIoLock.unlock();
        }
    }

    void readDimensionFromTxt() throws IOException {
        this.fileIoLock.lock();
        try {
            this.replaceWaypointLists(this.readFromTxtFile(this.dimensionFilePath));
        } finally {
            this.fileIoLock.unlock();
        }
    }

    void saveDimension() throws IOException {
        this.fileIoLock.lock();
        try {
            this.writeToFile(this.dimensionFilePath, this.snapshotWaypointLists());
        } finally {
            this.fileIoLock.unlock();
        }
    }

    private Gson getGson() {
        boolean excludeClientFields = WaypointList.excludeClientOnlyFields;
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(WaypointPos.class, new WaypointPos.WaypointPosAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .setExclusionStrategies(WaypointList.exclusionStrategy(excludeClientFields))
                .create();
    }

    private List<WaypointList> readFromTxtFile(Path filePath) throws IOException {
        List<WaypointList> waypointLists = new ArrayList<>();
        String currentListName = null;
        List<SimpleWaypoint> currentWaypoints = null;

        int waypointsNumber = 0;
        for (String line : Files.readAllLines(filePath)) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (line.startsWith("#")) {
                    if (currentListName != null) {
                        waypointLists.add(new WaypointList(
                                currentListName,
                                WaypointList.SERVER_N,
                                currentWaypoints
                        ));
                    }
                    currentListName = line.substring(1).trim();
                    currentWaypoints = new ArrayList<>();
                } else if (currentWaypoints != null) {
                    try {
                        SimpleWaypoint waypoint = SimpleWaypoint.fromString(line);
                        currentWaypoints.add(waypoint);
                        waypointsNumber++;
                    } catch (Exception e) {
                        WaypointServerCore.LOGGER.error("Failed to parse waypoint line: {}", line, e);
                    }
                }
            }
        }
        if (currentListName != null) {
            waypointLists.add(new WaypointList(
                    currentListName,
                    WaypointList.SERVER_N,
                    currentWaypoints
            ));
        }
        WaypointServerCore.LOGGER.info("Loaded {} lists and {} waypoints from old txt file: {}", waypointLists.size(), waypointsNumber, filePath);
        return waypointLists;
    }

    private List<WaypointList> readFromFile(Path filePath) throws IOException {
        ArrayList<WaypointList> waypointLists;
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            Type listType = new TypeToken<ArrayList<WaypointList>>() {}.getType();
            Gson gson = getGson();
            waypointLists = gson.fromJson(reader, listType);
        }
        if (waypointLists == null) {
            waypointLists = new ArrayList<>();
        }
        int waypointsNumber = 0;
        for (WaypointList waypointList : waypointLists) {
            waypointsNumber += waypointList.size();
        }
        WaypointServerCore.LOGGER.info("Loaded {} lists and {} waypoints from file: {}", waypointLists.size(), waypointsNumber, filePath);
        return waypointLists;
    }

    private void writeToFile(Path filePath, List<WaypointList> waypointLists) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempDirectory = parent == null ? filePath.toAbsolutePath().getParent() : parent;
        Path tempFile = Files.createTempFile(tempDirectory, filePath.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile.toFile()), StandardCharsets.UTF_8))) {
                Gson gson = getGson();
                gson.toJson(waypointLists, writer);
            }
            try {
                Files.move(
                        tempFile,
                        filePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            WaypointServerCore.LOGGER.info("Saved {} waypoint lists to file: {}", waypointLists.size(), filePath);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public boolean hasNoWaypoints() {
        return this.readState(() -> {
            for (WaypointList waypointList : this.waypointListMap.values()) {
                if (!waypointList.isEmpty()) {
                    return false;
                }
            }
            return true;
        });
    }

    public boolean isEmpty() {
        return this.readState(this.waypointListMap::isEmpty);
    }

    public String getDimensionName() {
        return this.dimensionName;
    }

    /**
     * returns a immutable shallow copy of the list
     * */
    public @Unmodifiable List<WaypointList> getWaypointLists() {
        return this.readState(() -> Collections.unmodifiableList(new ArrayList<>(this.waypointListMap.values())));
    }

    public @Unmodifiable Map<String, WaypointList> getWaypointListMap() {
        return this.readState(() -> Collections.unmodifiableMap(new HashMap<>(this.waypointListMap)));
    }

    public @Nullable WaypointList getWaypointListByName(String name) {
        return this.readState(() -> this.waypointListMap.get(name));
    }

    /**
     * will replace the existing list with the same name
     * */
    void addWaypointList(WaypointList waypointList) {
        this.writeState(() -> this.putWaypointList(waypointList));
    }

    /**
     * will replace the existing list with the same name
     * */
    void addWaypointLists(Collection<WaypointList> waypointLists) {
        this.writeState(() -> {
            for (WaypointList waypointList : waypointLists) {
                this.putWaypointList(waypointList);
            }
        });
    }

    @Nullable WaypointList removeWaypointListByName(String name) {
        return this.writeState(() -> {
            WaypointList removed = this.waypointListMap.remove(name);
            if (removed != null) {
                removed.detachOwner(this.mutationAuthority);
            }
            return removed;
        });
    }

    //? if !paper {
    SimpleWaypoint addWaypointFromRemoteServer(
            String listName,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            if (waypointList == null) {
                waypointList = WaypointList.build(listName, syncId);
                this.putWaypointList(waypointList);
            }
            return waypointList.addFromRemoteServer(
                    this.mutationAuthority,
                    waypoint,
                    syncId
            );
        });
    }

    @Nullable SimpleWaypoint updateWaypointFromRemoteServer(
            String listName,
            String oldName,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            return waypointList == null
                    ? null
                    : waypointList.updateFromRemoteServer(
                            this.mutationAuthority,
                            oldName,
                            waypoint,
                            syncId
                    );
        });
    }

    @Nullable SimpleWaypoint removeWaypointFromRemoteServer(
            String listName,
            String waypointName,
            int syncId
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            return waypointList == null
                    ? null
                    : waypointList.removeFromRemoteServer(
                            this.mutationAuthority,
                            waypointName,
                            syncId
                    );
        });
    }
    //?}

    WaypointFilesManagerCore.AddWaypointResult addWaypointIfAbsent(
            String listName,
            SimpleWaypoint waypoint,
            boolean dimensionCreated
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            boolean listCreated = false;
            if (waypointList == null) {
                waypointList = WaypointList.buildByServer(listName);
                this.putWaypointList(waypointList);
                listCreated = true;
            }
            WaypointList.ServerAddResult result = waypointList.addByServerIfAbsent(
                    this.mutationAuthority,
                    waypoint
            );
            WaypointFilesManagerCore.AddWaypointStatus status =
                    result.status() == WaypointList.ServerAddStatus.ADDED
                            ? WaypointFilesManagerCore.AddWaypointStatus.ADDED
                            : WaypointFilesManagerCore.AddWaypointStatus.DUPLICATE;
            return new WaypointFilesManagerCore.AddWaypointResult(
                    status,
                    this,
                    waypointList,
                    result.waypoint(),
                    result.waypointSnapshot(),
                    result.syncNum(),
                    dimensionCreated,
                    listCreated
            );
        });
    }

    WaypointFilesManagerCore.AddWaypointListResult addWaypointListIfAbsent(
            String listName,
            boolean dimensionCreated
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            WaypointFilesManagerCore.AddWaypointListStatus status;
            if (waypointList == null) {
                waypointList = WaypointList.buildByServer(listName);
                this.putWaypointList(waypointList);
                status = WaypointFilesManagerCore.AddWaypointListStatus.ADDED;
            } else {
                status = WaypointFilesManagerCore.AddWaypointListStatus.EXISTS;
            }
            return new WaypointFilesManagerCore.AddWaypointListResult(
                    status,
                    this,
                    waypointList,
                    dimensionCreated
            );
        });
    }

    WaypointFilesManagerCore.RemoveWaypointListResult removeWaypointListIfEmpty(String listName) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            if (waypointList == null) {
                return new WaypointFilesManagerCore.RemoveWaypointListResult(
                        WaypointFilesManagerCore.RemoveWaypointListStatus.LIST_NOT_FOUND,
                        this,
                        null
                );
            }
            synchronized (waypointList) {
                if (!waypointList.isEmpty()) {
                    return new WaypointFilesManagerCore.RemoveWaypointListResult(
                            WaypointFilesManagerCore.RemoveWaypointListStatus.NON_EMPTY,
                            this,
                            waypointList
                    );
                }
                this.waypointListMap.remove(listName, waypointList);
                waypointList.detachOwner(this.mutationAuthority);
                return new WaypointFilesManagerCore.RemoveWaypointListResult(
                        WaypointFilesManagerCore.RemoveWaypointListStatus.REMOVED,
                        this,
                        waypointList
                );
            }
        });
    }

    WaypointFilesManagerCore.UpdateWaypointResult updateWaypoint(
            String listName,
            String oldName,
            String newName,
            String initials,
            WaypointPos waypointPos,
            int rgb,
            int yaw,
            boolean global
    ) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            if (waypointList == null) {
                return WaypointFilesManagerCore.UpdateWaypointResult.listNotFound(this);
            }
            WaypointList.ServerUpdateResult result = waypointList.updateByServer(
                    this.mutationAuthority,
                    oldName,
                    newName,
                    initials,
                    waypointPos,
                    rgb,
                    yaw,
                    global
            );
            WaypointFilesManagerCore.UpdateWaypointStatus status = switch (result.status()) {
                case UPDATED -> WaypointFilesManagerCore.UpdateWaypointStatus.UPDATED;
                case NAME_USED -> WaypointFilesManagerCore.UpdateWaypointStatus.NAME_USED;
                case IDENTICAL -> WaypointFilesManagerCore.UpdateWaypointStatus.IDENTICAL;
                case EMPTY -> WaypointFilesManagerCore.UpdateWaypointStatus.LIST_EMPTY;
                case NOT_FOUND -> WaypointFilesManagerCore.UpdateWaypointStatus.WAYPOINT_NOT_FOUND;
            };
            return new WaypointFilesManagerCore.UpdateWaypointResult(
                    status,
                    this,
                    waypointList,
                    result.waypoint(),
                    result.beforeSnapshot(),
                    result.afterSnapshot(),
                    result.syncNum()
            );
        });
    }

    WaypointFilesManagerCore.RemoveWaypointResult removeWaypoint(String listName, String waypointName) {
        return this.writeState(() -> {
            WaypointList waypointList = this.waypointListMap.get(listName);
            if (waypointList == null) {
                return WaypointFilesManagerCore.RemoveWaypointResult.listNotFound(this);
            }
            WaypointList.ServerRemoveResult result = waypointList.removeByServer(
                    this.mutationAuthority,
                    waypointName
            );
            if (result.status() != WaypointList.ServerRemoveStatus.REMOVED) {
                WaypointFilesManagerCore.RemoveWaypointStatus status =
                        result.status() == WaypointList.ServerRemoveStatus.EMPTY
                                ? WaypointFilesManagerCore.RemoveWaypointStatus.LIST_EMPTY
                                : WaypointFilesManagerCore.RemoveWaypointStatus.WAYPOINT_NOT_FOUND;
                return new WaypointFilesManagerCore.RemoveWaypointResult(
                        status,
                        this,
                        waypointList,
                        null,
                        null,
                        result.syncNum()
                );
            }
            return new WaypointFilesManagerCore.RemoveWaypointResult(
                    WaypointFilesManagerCore.RemoveWaypointStatus.REMOVED,
                    this,
                    waypointList,
                    result.waypoint(),
                    result.waypointSnapshot(),
                    result.syncNum()
            );
        });
    }

    private List<WaypointList> snapshotWaypointLists() {
        return this.readState(() -> {
            List<WaypointList> snapshot = new ArrayList<>(this.waypointListMap.size());
            for (WaypointList waypointList : this.waypointListMap.values()) {
                snapshot.add(waypointList.deepCopy());
            }
            return Collections.unmodifiableList(snapshot);
        });
    }

    private void replaceWaypointLists(Collection<WaypointList> waypointLists) {
        this.writeState(() -> {
            for (WaypointList waypointList : this.waypointListMap.values()) {
                waypointList.detachOwner(this.mutationAuthority);
            }
            this.waypointListMap.clear();
            for (WaypointList waypointList : waypointLists) {
                this.putWaypointList(waypointList);
            }
        });
    }

    private void putWaypointList(WaypointList waypointList) {
        waypointList.attachOwner(this.mutationAuthority);
        WaypointList previous = this.waypointListMap.put(waypointList.name(), waypointList);
        if (previous != null && previous != waypointList) {
            previous.detachOwner(this.mutationAuthority);
        }
    }

    private <T> T readState(Supplier<T> action) {
        this.stateLock.readLock().lock();
        try {
            return action.get();
        } finally {
            this.stateLock.readLock().unlock();
        }
    }

    private <T> T writeState(Supplier<T> action) {
        this.stateLock.writeLock().lock();
        try {
            return action.get();
        } finally {
            this.stateLock.writeLock().unlock();
        }
    }

    private void writeState(Runnable action) {
        this.stateLock.writeLock().lock();
        try {
            action.run();
        } finally {
            this.stateLock.writeLock().unlock();
        }
    }

    /**
     * Unforgeable capability used to keep server list mutations owner-mediated even though
     * the list model lives in a different Java package.
     */
    public static final class MutationAuthority {
        private MutationAuthority() {
        }
    }

    void deleteDimensionFile() {
        this.fileIoLock.lock();
        try {
            try {
                Files.deleteIfExists(this.dimensionFilePath);
                WaypointServerCore.LOGGER.info("Deleted dimension file: {}", this.dimensionFilePath);
            } catch (IOException e) {
                WaypointServerCore.LOGGER.error("Failed to delete dimension file: {}", this.dimensionFilePath, e);
            }
        } finally {
            this.fileIoLock.unlock();
        }
    }

    public static WaypointFileManager buildFromDimensionName(@NotNull Path waypointFileFolder, @NotNull String dimensionName) {
        return new WaypointFileManager(dimensionName.replace("/", "%").replace(":", "$"), dimensionName, waypointFileFolder);
    }

    public static WaypointFileManager buildFromFileName(@NotNull Path waypointFileFolder, @NotNull String fileName) {
        return new WaypointFileManager(fileName, fileName.replace("%", "/").replace("$", ":"), waypointFileFolder);
    }
}
