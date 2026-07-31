package _959.server_waypoint.core;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

/**
 * Load and save all waypoint files in the specified directory.
 *
 * <p>Mutations are linearized per dimension. Lifecycle publication and persistence are
 * coordinated at the root, and a new lifecycle generation waits for already committed
 * callbacks to finish. Callbacks are delivered in commit order on their invoking threads after
 * model locks have been released. A reentrant callback that is not yet next for its dimension
 * is deferred on that same thread until the outer callback releases its turn, preserving order
 * without creating cross-dimension callback cycles; a deferred failure is reported by the
 * outermost mutation call. Lifecycle transitions are rejected from inside mutation callbacks
 * because their synchronous ordering cannot be preserved there. Different dimensions can be
 * mutated concurrently.</p>
 */
public class WaypointFilesManagerCore {
    public static final Logger LOGGER = LoggerFactory.getLogger("waypoint_files_manager");
    protected volatile Map<String, WaypointFileManager> fileManagerMap;
    protected volatile Path waypointFilesDir;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock mutationAdmissionLock = new ReentrantReadWriteLock(true);
    private final ReentrantLock callbackCompletionLock = new ReentrantLock(true);
    private final Condition noRegisteredCallbacks = this.callbackCompletionLock.newCondition();
    private final Map<String, DimensionMutationLane> dimensionMutationLanes = new ConcurrentHashMap<>();
    private final Map<Path, ReentrantLock> persistenceLocks = new ConcurrentHashMap<>();
    private int registeredCallbacks;

    /**
     * initialize without waypointFilesDir set
     * */
    public WaypointFilesManagerCore() {
        this.fileManagerMap = new AtomicFileManagerMap();
    }

    public WaypointFilesManagerCore(Path waypointsDir) {
        this.waypointFilesDir = waypointsDir;
        this.fileManagerMap = new AtomicFileManagerMap();
    }

    public @UnmodifiableView Map<String, WaypointFileManager> getFileManagerMap() {
        return this.readLifecycle(() -> new LiveUnmodifiableMap<>(this.fileManagerMap));
    }

    public @Nullable Path getWaypointFilesDir() {
        return this.readLifecycle(() -> this.waypointFilesDir);
    }

    public @Unmodifiable List<Map.Entry<String, WaypointFileManager>> getSortedMap() {
        return this.readLifecycle(() -> {
            List<Map.Entry<String, WaypointFileManager>> entries = this.fileManagerMap.entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            entries.sort((a, b) -> dimensionNameComparator(a.getKey(), b.getKey()));
            return Collections.unmodifiableList(entries);
        });
    }

    public @Nullable WaypointFileManager getWaypointFileManager(String dimensionName) {
        return this.readLifecycle(() -> this.fileManagerMap.get(dimensionName));
    }

    public @NotNull WaypointFileManager getOrCreateWaypointFileManager(String dimensionName) {
        return this.mutateDimension(
                dimensionName,
                () -> this.getOrCreateWaypointFileManagerLocked(dimensionName, new AtomicBoolean())
        );
    }

    public AddWaypointResult addWaypoint(
            String dimensionName,
            String listName,
            SimpleWaypoint waypoint,
            Consumer<AddWaypointResult> resultAction
    ) {
        return this.mutateDimension(
                dimensionName,
                () -> {
                    AtomicBoolean dimensionCreated = new AtomicBoolean();
                    WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                            dimensionName,
                            dimensionCreated
                    );
                    return fileManager.addWaypointIfAbsent(
                            listName,
                            waypoint,
                            dimensionCreated.get()
                    );
                },
                resultAction
        );
    }

    public AddWaypointListResult addWaypointList(
            String dimensionName,
            String listName,
            Consumer<AddWaypointListResult> resultAction
    ) {
        return this.mutateDimension(
                dimensionName,
                () -> {
                    AtomicBoolean dimensionCreated = new AtomicBoolean();
                    WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                            dimensionName,
                            dimensionCreated
                    );
                    return fileManager.addWaypointListIfAbsent(
                            listName,
                            dimensionCreated.get()
                    );
                },
                resultAction
        );
    }

    public RemoveWaypointListResult removeWaypointList(
            String dimensionName,
            String listName,
            Consumer<RemoveWaypointListResult> resultAction
    ) {
        return this.mutateDimension(
                dimensionName,
                () -> {
                    WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
                    return fileManager == null
                            ? RemoveWaypointListResult.dimensionNotFound()
                            : fileManager.removeWaypointListIfEmpty(listName);
                },
                resultAction
        );
    }

    public UpdateWaypointResult updateWaypointProperties(
            String dimensionName,
            String listName,
            String oldName,
            String newName,
            String initials,
            WaypointPos waypointPos,
            int rgb,
            int yaw,
            boolean global,
            Consumer<UpdateWaypointResult> resultAction
    ) {
        return this.mutateDimension(
                dimensionName,
                () -> {
                    WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
                    return fileManager == null
                            ? UpdateWaypointResult.dimensionNotFound()
                            : fileManager.updateWaypoint(
                                    listName,
                                    oldName,
                                    newName,
                                    initials,
                                    waypointPos,
                                    rgb,
                                    yaw,
                                    global
                            );
                },
                resultAction
        );
    }

    public RemoveWaypointResult removeWaypoint(
            String dimensionName,
            String listName,
            String waypointName,
            Consumer<RemoveWaypointResult> resultAction
    ) {
        return this.mutateDimension(
                dimensionName,
                () -> {
                    WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
                    return fileManager == null
                            ? RemoveWaypointResult.dimensionNotFound()
                            : fileManager.removeWaypoint(listName, waypointName);
                },
                resultAction
        );
    }

    /**
     * Add an empty waypoint list manager to this files manager by dimension name </br>
     * */
    public WaypointFileManager addWaypointFileManager(String dimensionName) {
        return this.mutateDimension(dimensionName, () -> {
            AtomicBoolean dimensionCreated = new AtomicBoolean();
            WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                    dimensionName,
                    dimensionCreated
            );
            if (!dimensionCreated.get()) {
                LOGGER.warn("Duplicate dimension key: {}", dimensionName);
            }
            return fileManager;
        });
    }

    /**
     * Replaces one list through the owning dimension lane.
     */
    public WaypointFileManager putWaypointList(String dimensionName, WaypointList waypointList) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                    dimensionName,
                    new AtomicBoolean()
            );
            fileManager.addWaypointList(waypointList);
            return fileManager;
        });
    }

    /**
     * Replaces lists through the owning dimension lane.
     */
    public WaypointFileManager putWaypointLists(
            String dimensionName,
            Collection<WaypointList> waypointLists
    ) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                    dimensionName,
                    new AtomicBoolean()
            );
            fileManager.addWaypointLists(waypointLists);
            return fileManager;
        });
    }

    //? if !paper {
    public SimpleWaypoint addWaypointFromRemoteServer(
            String dimensionName,
            String listName,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.getOrCreateWaypointFileManagerLocked(
                    dimensionName,
                    new AtomicBoolean()
            );
            return fileManager.addWaypointFromRemoteServer(listName, waypoint, syncId);
        });
    }

    public @Nullable SimpleWaypoint updateWaypointFromRemoteServer(
            String dimensionName,
            String listName,
            String oldName,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
            return fileManager == null
                    ? null
                    : fileManager.updateWaypointFromRemoteServer(
                            listName,
                            oldName,
                            waypoint,
                            syncId
                    );
        });
    }

    public @Nullable SimpleWaypoint removeWaypointFromRemoteServer(
            String dimensionName,
            String listName,
            String waypointName,
            int syncId
    ) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
            return fileManager == null
                    ? null
                    : fileManager.removeWaypointFromRemoteServer(
                            listName,
                            waypointName,
                            syncId
                    );
        });
    }
    //?}

    /**
     * Removes a list without the server-side empty-list constraint. This is used when applying
     * an authoritative remote snapshot and is still serialized through the dimension lane.
     */
    public @Nullable WaypointList removeWaypointListImmediately(
            String dimensionName,
            String listName
    ) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager fileManager = this.fileManagerMap.get(dimensionName);
            return fileManager == null ? null : fileManager.removeWaypointListByName(listName);
        });
    }

    /**
     * Atomically publishes an empty lifecycle generation.
     */
    public void clearWaypointFileManagers() {
        this.runLifecycleTransition(() -> this.publishLoadedManagers(Map.of()));
    }

    /**
     * Builds and atomically publishes a complete in-memory waypoint generation.
     */
    public void replaceWaypointData(
            Map<String, ? extends Collection<WaypointList>> waypointListsByDimension
    ) {
        this.runLifecycleTransition(() -> {
            Map<String, WaypointFileManager> replacement = new LinkedHashMap<>();
            for (Map.Entry<String, ? extends Collection<WaypointList>> entry
                    : waypointListsByDimension.entrySet()) {
                WaypointFileManager fileManager = WaypointFileManager.buildFromDimensionName(
                        Objects.requireNonNull(this.waypointFilesDir, "waypointFilesDir"),
                        entry.getKey()
                );
                fileManager.addWaypointLists(entry.getValue());
                replacement.put(entry.getKey(), fileManager);
            }
            this.publishLoadedManagers(replacement);
        });
    }

    public @Nullable WaypointFileManager removeWaypointFileManager(String dimensionName, boolean deleteFile) {
        return this.mutateDimension(dimensionName, () -> {
            WaypointFileManager removedManager = this.fileManagerMap.remove(dimensionName);
            if (deleteFile && removedManager != null) {
                this.withPersistenceLock(removedManager, removedManager::deleteDimensionFile);
            }
            return removedManager;
        });
    }

    /**
     * Creates a new folder if waypointFilesDir does not exist </br>
     * Read all waypoint files in waypointFilesDir, clears all previously loaded waypoints
    * */
    protected void initOrReadWaypointFiles() throws IOException {
        try {
            this.runLifecycleTransition(() -> {
                Path waypointDirectory = this.waypointFilesDir;
                if (waypointDirectory == null) {
                    LOGGER.warn("No waypoint files directory provided.");
                    return;
                }
                try {
                    this.publishLoadedManagers(this.loadWaypointFiles(waypointDirectory));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Map<String, WaypointFileManager> loadWaypointFiles(Path waypointDirectory) throws IOException {
        try {
            if (!Files.exists(waypointDirectory) || !Files.isDirectory(waypointDirectory)) {
                Files.createDirectories(waypointDirectory);
                WaypointServerCore.LOGGER.info("Created waypoints directory at: {}", waypointDirectory);
            }
        } catch (IOException e) {
            WaypointServerCore.LOGGER.error("Failed to initialize waypoints directory");
            throw e;
        }
        List<WaypointFileManager> fileManagers = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(waypointDirectory)) {
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
                WaypointFileManager fileManager = WaypointFileManager.buildFromFileName(waypointDirectory, fileName);
                try {
                    if (isTxt) {
                        // convert to json format
                        fileManager.readDimensionFromTxt();
                        fileManager.saveDimension();
                    } else {
                        fileManager.readDimension();
                    }
                    fileManagers.add(fileManager);
                } catch (IOException e) {
                    WaypointServerCore.LOGGER.error("Failed to load dimension file", e);
                    throw e;
                }
            }
        }
        // sort by dimension names to get rid of random file reading order
        fileManagers.sort((a, b) -> dimensionNameComparator(a.getDimensionName(), b.getDimensionName()));
        Map<String, WaypointFileManager> loadedManagers = new LinkedHashMap<>();
        fileManagers.forEach(fileManager -> loadedManagers.put(fileManager.getDimensionName(), fileManager));
        return loadedManagers;
    }

    private void publishLoadedManagers(Map<String, WaypointFileManager> loadedManagers) {
        if (this.fileManagerMap instanceof AtomicFileManagerMap atomicMap) {
            atomicMap.replaceContents(loadedManagers);
        } else {
            this.fileManagerMap = new AtomicFileManagerMap(loadedManagers);
        }
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
        List<WaypointFileManager> fileManagers = this.readLifecycle(
                () -> List.copyOf(this.fileManagerMap.values())
        );
        for (WaypointFileManager fileManager : fileManagers) {
            try {
                this.saveWaypointFile(fileManager);
            } catch (Exception e) {
                LOGGER.error("Failed to save dimension file {}", fileManager.getDimensionFile(), e);
            }
        }
    }

    /**
     * Saves the currently active manager for the supplied manager's dimension.
     * A delayed save from an older lifecycle generation therefore cannot overwrite newer state.
     */
    public void saveWaypointFile(WaypointFileManager requestedManager) throws IOException {
        try {
            this.mutateDimension(requestedManager.getDimensionName(), () -> {
                WaypointFileManager activeManager = this.fileManagerMap.get(
                        requestedManager.getDimensionName()
                );
                if (activeManager != null) {
                    try {
                        this.saveWaypointFileLocked(activeManager);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return null;
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
    * Change the directory of waypoint files and load all waypoint files
    * */
    public void changeWaypointFilesDir(Path newPath) {
        this.runLifecycleTransition(() -> {
            try {
                Map<String, WaypointFileManager> loadedManagers = this.loadWaypointFiles(newPath);
                this.waypointFilesDir = newPath;
                this.publishLoadedManagers(loadedManagers);
            } catch (IOException e) {
                WaypointServerCore.LOGGER.error(
                        "Failed to load waypoints file from {}: {}",
                        newPath,
                        e
                );
                throw new RuntimeException(e);
            }
        });
    }

    protected void withLifecycleWriteLock(Runnable action) {
        this.runLifecycleTransition(action);
    }

    private void runLifecycleTransition(Runnable action) {
        CallbackDispatchContext callbackContext = DimensionMutationLane.callbackContext();
        if (callbackContext != null && callbackContext.isExecutingCallback()) {
            throw new IllegalStateException(
                    "Lifecycle transitions cannot run from waypoint mutation callbacks"
            );
        }
        this.runLifecycleTransitionNow(action);
    }

    private void runLifecycleTransitionNow(Runnable action) {
        while (true) {
            this.awaitRegisteredCallbacks();
            this.mutationAdmissionLock.writeLock().lock();
            try {
                this.lifecycleLock.writeLock().lock();
                try {
                    if (this.hasRegisteredCallbacks()) {
                        continue;
                    }
                    action.run();
                    return;
                } finally {
                    this.lifecycleLock.writeLock().unlock();
                }
            } finally {
                this.mutationAdmissionLock.writeLock().unlock();
            }
        }
    }

    private void awaitRegisteredCallbacks() {
        this.callbackCompletionLock.lock();
        try {
            while (this.registeredCallbacks != 0) {
                this.noRegisteredCallbacks.awaitUninterruptibly();
            }
        } finally {
            this.callbackCompletionLock.unlock();
        }
    }

    private void registerCallback() {
        this.callbackCompletionLock.lock();
        try {
            this.registeredCallbacks++;
        } finally {
            this.callbackCompletionLock.unlock();
        }
    }

    private boolean hasRegisteredCallbacks() {
        this.callbackCompletionLock.lock();
        try {
            return this.registeredCallbacks != 0;
        } finally {
            this.callbackCompletionLock.unlock();
        }
    }

    private void completeCallback() {
        this.callbackCompletionLock.lock();
        try {
            this.registeredCallbacks--;
            if (this.registeredCallbacks < 0) {
                throw new IllegalStateException("Waypoint callback registration underflow");
            }
            if (this.registeredCallbacks == 0) {
                this.noRegisteredCallbacks.signalAll();
            }
        } finally {
            this.callbackCompletionLock.unlock();
        }
    }

    private boolean acquireMutationAdmission() {
        CallbackDispatchContext callbackContext = DimensionMutationLane.callbackContext();
        if (callbackContext != null && callbackContext.isExecutingCallback()) {
            return false;
        }
        this.mutationAdmissionLock.readLock().lock();
        return true;
    }

    private void releaseMutationAdmission(boolean acquired) {
        if (acquired) {
            this.mutationAdmissionLock.readLock().unlock();
        }
    }

    private WaypointFileManager getOrCreateWaypointFileManagerLocked(
            String dimensionName,
            AtomicBoolean dimensionCreated
    ) {
        return this.fileManagerMap.computeIfAbsent(dimensionName, ignored -> {
            dimensionCreated.set(true);
            return WaypointFileManager.buildFromDimensionName(this.waypointFilesDir, dimensionName);
        });
    }

    protected final <T> T readLifecycle(Supplier<T> action) {
        this.lifecycleLock.readLock().lock();
        try {
            return action.get();
        } finally {
            this.lifecycleLock.readLock().unlock();
        }
    }

    private <T> T mutateDimension(String dimensionName, Supplier<T> action) {
        boolean admissionAcquired = this.acquireMutationAdmission();
        try {
            this.lifecycleLock.readLock().lock();
            try {
                DimensionMutationLane mutationLane = this.dimensionMutationLanes.computeIfAbsent(
                        dimensionName,
                        ignored -> new DimensionMutationLane()
                );
                mutationLane.mutationLock.lock();
                try {
                    return action.get();
                } finally {
                    mutationLane.mutationLock.unlock();
                }
            } finally {
                this.lifecycleLock.readLock().unlock();
            }
        } finally {
            this.releaseMutationAdmission(admissionAcquired);
        }
    }

    private <T> T mutateDimension(
            String dimensionName,
            Supplier<T> action,
            Consumer<T> resultAction
    ) {
        Objects.requireNonNull(resultAction, "resultAction");
        T result;
        CallbackEvent callbackEvent;
        DimensionMutationLane mutationLane;
        boolean admissionAcquired = this.acquireMutationAdmission();
        try {
            this.lifecycleLock.readLock().lock();
            try {
                mutationLane = this.dimensionMutationLanes.computeIfAbsent(
                        dimensionName,
                        ignored -> new DimensionMutationLane()
                );
                mutationLane.mutationLock.lock();
                try {
                    result = action.get();
                    callbackEvent = mutationLane.enqueueCallback(
                            this,
                            () -> resultAction.accept(result)
                    );
                    this.registerCallback();
                } finally {
                    mutationLane.mutationLock.unlock();
                }
            } finally {
                this.lifecycleLock.readLock().unlock();
            }
        } finally {
            this.releaseMutationAdmission(admissionAcquired);
        }
        mutationLane.dispatchAndAwait(callbackEvent);
        return result;
    }

    private void saveWaypointFileLocked(WaypointFileManager fileManager) throws IOException {
        ReentrantLock persistenceLock = this.getPersistenceLock(fileManager);
        persistenceLock.lock();
        try {
            fileManager.saveDimension();
        } finally {
            persistenceLock.unlock();
        }
    }

    private void withPersistenceLock(WaypointFileManager fileManager, Runnable action) {
        ReentrantLock persistenceLock = this.getPersistenceLock(fileManager);
        persistenceLock.lock();
        try {
            action.run();
        } finally {
            persistenceLock.unlock();
        }
    }

    private ReentrantLock getPersistenceLock(WaypointFileManager fileManager) {
        Path persistencePath = fileManager.getDimensionFile().toAbsolutePath().normalize();
        return this.persistenceLocks.computeIfAbsent(persistencePath, ignored -> new ReentrantLock(true));
    }

    private static final class DimensionMutationLane {
        private static final ThreadLocal<CallbackDispatchContext> CALLBACK_CONTEXT =
                new ThreadLocal<>();
        private final ReentrantLock mutationLock = new ReentrantLock(true);
        private final ReentrantLock callbackLock = new ReentrantLock(true);
        private final Condition callbackTurn = this.callbackLock.newCondition();
        private long nextCallbackSequence;
        private long callbackSequence;

        private static @Nullable CallbackDispatchContext callbackContext() {
            return CALLBACK_CONTEXT.get();
        }

        private CallbackEvent enqueueCallback(
                WaypointFilesManagerCore owner,
                Runnable callback
        ) {
            return new CallbackEvent(owner, this.nextCallbackSequence++, callback);
        }

        private void dispatchAndAwait(CallbackEvent targetEvent) {
            CallbackDispatchContext activeContext = CALLBACK_CONTEXT.get();
            if (activeContext != null) {
                if (this.isCallbackTurn(targetEvent.sequence())) {
                    this.executeInSequence(targetEvent);
                    targetEvent.rethrowFailure();
                } else {
                    activeContext.defer(this, targetEvent);
                }
                return;
            }

            CallbackDispatchContext context = new CallbackDispatchContext();
            CALLBACK_CONTEXT.set(context);
            Throwable failure = null;
            try {
                this.executeInSequence(targetEvent);
                failure = targetEvent.failure();
                while (true) {
                    DeferredCallback deferredCallback = context.pollCallback();
                    if (deferredCallback == null) {
                        break;
                    }
                    deferredCallback.lane().executeInSequence(deferredCallback.event());
                    failure = combineFailures(failure, deferredCallback.event().failure());
                }
            } finally {
                CALLBACK_CONTEXT.remove();
            }
            CallbackEvent.rethrowFailure(failure);
        }

        private boolean isCallbackTurn(long sequence) {
            this.callbackLock.lock();
            try {
                return sequence == this.callbackSequence;
            } finally {
                this.callbackLock.unlock();
            }
        }

        private void executeInSequence(CallbackEvent event) {
            this.awaitCallbackTurn(event.sequence());
            CallbackDispatchContext context = Objects.requireNonNull(
                    CALLBACK_CONTEXT.get(),
                    "callbackContext"
            );
            context.enterCallback();
            try {
                event.execute();
            } finally {
                context.exitCallback();
                try {
                    this.completeCallback(event.sequence());
                } finally {
                    event.owner().completeCallback();
                }
            }
        }

        private void awaitCallbackTurn(long sequence) {
            this.callbackLock.lock();
            try {
                while (sequence != this.callbackSequence) {
                    this.callbackTurn.awaitUninterruptibly();
                }
            } finally {
                this.callbackLock.unlock();
            }
        }

        private void completeCallback(long sequence) {
            this.callbackLock.lock();
            try {
                if (sequence != this.callbackSequence) {
                    throw new IllegalStateException("Waypoint callback completed out of order");
                }
                this.callbackSequence++;
                this.callbackTurn.signalAll();
            } finally {
                this.callbackLock.unlock();
            }
        }

        private static Throwable combineFailures(Throwable primary, Throwable additional) {
            if (additional == null) {
                return primary;
            }
            if (primary == null) {
                return additional;
            }
            if (primary != additional) {
                primary.addSuppressed(additional);
            }
            return primary;
        }
    }

    private static final class CallbackDispatchContext {
        private final Deque<DeferredCallback> deferredCallbacks = new ArrayDeque<>();
        private int callbackDepth;

        private void defer(DimensionMutationLane lane, CallbackEvent event) {
            this.deferredCallbacks.addLast(new DeferredCallback(lane, event));
        }

        private void enterCallback() {
            this.callbackDepth++;
        }

        private void exitCallback() {
            this.callbackDepth--;
            if (this.callbackDepth < 0) {
                throw new IllegalStateException("Waypoint callback depth underflow");
            }
        }

        private boolean isExecutingCallback() {
            return this.callbackDepth != 0;
        }

        private @Nullable DeferredCallback pollCallback() {
            return this.deferredCallbacks.pollFirst();
        }
    }

    private record DeferredCallback(DimensionMutationLane lane, CallbackEvent event) {
    }

    private static final class CallbackEvent {
        private final WaypointFilesManagerCore owner;
        private final long sequence;
        private final Runnable callback;
        private Throwable failure;

        private CallbackEvent(
                WaypointFilesManagerCore owner,
                long sequence,
                Runnable callback
        ) {
            this.owner = owner;
            this.sequence = sequence;
            this.callback = callback;
        }

        private void execute() {
            try {
                this.callback.run();
            } catch (Throwable throwable) {
                this.failure = throwable;
            }
        }

        private long sequence() {
            return this.sequence;
        }

        private WaypointFilesManagerCore owner() {
            return this.owner;
        }

        private Throwable failure() {
            return this.failure;
        }

        private void rethrowFailure() {
            rethrowFailure(this.failure);
        }

        private static void rethrowFailure(Throwable callbackFailure) {
            if (callbackFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (callbackFailure instanceof Error error) {
                throw error;
            }
            if (callbackFailure != null) {
                throw new RuntimeException(callbackFailure);
            }
        }
    }

    private static final class AtomicFileManagerMap
            extends AbstractMap<String, WaypointFileManager>
            implements ConcurrentMap<String, WaypointFileManager> {
        private final AtomicReference<ConcurrentMap<String, WaypointFileManager>> contents;

        private AtomicFileManagerMap() {
            this.contents = new AtomicReference<>(new ConcurrentHashMap<>());
        }

        private AtomicFileManagerMap(Map<String, WaypointFileManager> contents) {
            this.contents = new AtomicReference<>(new ConcurrentHashMap<>(contents));
        }

        private void replaceContents(Map<String, WaypointFileManager> newContents) {
            this.contents.set(new ConcurrentHashMap<>(newContents));
        }

        @Override
        public Set<Entry<String, WaypointFileManager>> entrySet() {
            Set<Entry<String, WaypointFileManager>> entries = new HashSet<>();
            this.contents.get().forEach((key, value) -> entries.add(Map.entry(key, value)));
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public int size() {
            return this.contents.get().size();
        }

        @Override
        public boolean isEmpty() {
            return this.contents.get().isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return this.contents.get().containsKey(key);
        }

        @Override
        public WaypointFileManager get(Object key) {
            return this.contents.get().get(key);
        }

        @Override
        public WaypointFileManager put(String key, WaypointFileManager value) {
            return this.contents.get().put(key, value);
        }

        @Override
        public void putAll(Map<? extends String, ? extends WaypointFileManager> map) {
            this.contents.get().putAll(map);
        }

        @Override
        public WaypointFileManager remove(Object key) {
            return this.contents.get().remove(key);
        }

        @Override
        public void clear() {
            this.contents.set(new ConcurrentHashMap<>());
        }

        @Override
        public WaypointFileManager putIfAbsent(String key, WaypointFileManager value) {
            return this.contents.get().putIfAbsent(key, value);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return this.contents.get().remove(key, value);
        }

        @Override
        public boolean replace(String key, WaypointFileManager oldValue, WaypointFileManager newValue) {
            return this.contents.get().replace(key, oldValue, newValue);
        }

        @Override
        public WaypointFileManager replace(String key, WaypointFileManager value) {
            return this.contents.get().replace(key, value);
        }

        @Override
        public WaypointFileManager computeIfAbsent(
                String key,
                java.util.function.Function<? super String, ? extends WaypointFileManager> mappingFunction
        ) {
            return this.contents.get().computeIfAbsent(key, mappingFunction);
        }
    }

    private static final class LiveUnmodifiableMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> delegate;

        private LiveUnmodifiableMap(Map<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            Set<Entry<K, V>> entries = new HashSet<>();
            this.delegate.forEach((key, value) -> entries.add(Map.entry(key, value)));
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return this.delegate.containsKey(key);
        }

        @Override
        public V get(Object key) {
            return this.delegate.get(key);
        }
    }

    public enum AddWaypointStatus {
        ADDED,
        DUPLICATE
    }

    public record AddWaypointResult(
            AddWaypointStatus status,
            WaypointFileManager fileManager,
            WaypointList waypointList,
            SimpleWaypoint waypoint,
            SimpleWaypoint waypointSnapshot,
            int syncNum,
            boolean dimensionCreated,
            boolean listCreated
    ) {
    }

    public enum AddWaypointListStatus {
        ADDED,
        EXISTS
    }

    public record AddWaypointListResult(
            AddWaypointListStatus status,
            WaypointFileManager fileManager,
            WaypointList waypointList,
            boolean dimensionCreated
    ) {
    }

    public enum RemoveWaypointListStatus {
        REMOVED,
        DIMENSION_NOT_FOUND,
        LIST_NOT_FOUND,
        NON_EMPTY
    }

    public record RemoveWaypointListResult(
            RemoveWaypointListStatus status,
            @Nullable WaypointFileManager fileManager,
            @Nullable WaypointList waypointList
    ) {
        static RemoveWaypointListResult dimensionNotFound() {
            return new RemoveWaypointListResult(
                    RemoveWaypointListStatus.DIMENSION_NOT_FOUND,
                    null,
                    null
            );
        }
    }

    public enum UpdateWaypointStatus {
        UPDATED,
        DIMENSION_NOT_FOUND,
        LIST_NOT_FOUND,
        LIST_EMPTY,
        WAYPOINT_NOT_FOUND,
        NAME_USED,
        IDENTICAL
    }

    public record UpdateWaypointResult(
            UpdateWaypointStatus status,
            @Nullable WaypointFileManager fileManager,
            @Nullable WaypointList waypointList,
            @Nullable SimpleWaypoint waypoint,
            @Nullable SimpleWaypoint beforeSnapshot,
            @Nullable SimpleWaypoint afterSnapshot,
            int syncNum
    ) {
        static UpdateWaypointResult dimensionNotFound() {
            return new UpdateWaypointResult(
                    UpdateWaypointStatus.DIMENSION_NOT_FOUND,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }

        static UpdateWaypointResult listNotFound(WaypointFileManager fileManager) {
            return new UpdateWaypointResult(
                    UpdateWaypointStatus.LIST_NOT_FOUND,
                    fileManager,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }
    }

    public enum RemoveWaypointStatus {
        REMOVED,
        DIMENSION_NOT_FOUND,
        LIST_NOT_FOUND,
        LIST_EMPTY,
        WAYPOINT_NOT_FOUND
    }

    public record RemoveWaypointResult(
            RemoveWaypointStatus status,
            @Nullable WaypointFileManager fileManager,
            @Nullable WaypointList waypointList,
            @Nullable SimpleWaypoint waypoint,
            @Nullable SimpleWaypoint waypointSnapshot,
            int syncNum
    ) {
        static RemoveWaypointResult dimensionNotFound() {
            return new RemoveWaypointResult(
                    RemoveWaypointStatus.DIMENSION_NOT_FOUND,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }

        static RemoveWaypointResult listNotFound(WaypointFileManager fileManager) {
            return new RemoveWaypointResult(
                    RemoveWaypointStatus.LIST_NOT_FOUND,
                    fileManager,
                    null,
                    null,
                    null,
                    0
            );
        }
    }
}
