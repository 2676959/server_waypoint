package _959.server_waypoint.core.collection;

import _959.server_waypoint.config.PrivateWaypointConfig;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns isolated player stores. An owner gate precedes every dimension lane and is held through
 * the persistence attempt. Callers derive the UUID from their authenticated player and send
 * success feedback only after {@link #withOwner} returns. Managers are borrowed for the callback;
 * they must not be retained or mutated directly through their lists.
 */
public final class PrivateWaypointStore implements AutoCloseable {
    private final Path root;
    private final Supplier<PrivateWaypointConfig> limits;
    private final Map<UUID, Owner> owners = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private boolean closed;

    public PrivateWaypointStore(Path root, Supplier<PrivateWaypointConfig> limits) {
        this.root = Objects.requireNonNull(root, "root");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @FunctionalInterface
    public interface Operation<T> {
        T apply(WaypointFilesManagerCore files) throws IOException;
    }

    public <T> T withOwner(UUID ownerUuid, Operation<T> operation) throws IOException {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(operation, "operation");
        this.rejectReentrantLifecycle();
        this.lifecycle.readLock().lock();
        try {
            this.ensureOpen();
            Owner owner = this.lockCurrentOwner(ownerUuid);
            try {
                if (owner.files == null) {
                    OwnerFiles candidate = new OwnerFiles(ownerUuid, owner);
                    try {
                        candidate.load();
                    } catch (RuntimeException exception) {
                        throw new IOException("Cannot load private waypoints for " + ownerUuid, exception);
                    }
                    owner.files = candidate;
                }
                T value;
                try {
                    value = operation.apply(owner.files);
                } catch (IOException | RuntimeException | Error failure) {
                    try {
                        this.flush(owner);
                    } catch (IOException saveFailure) {
                        failure.addSuppressed(saveFailure);
                    }
                    throw failure;
                }
                this.flush(owner);
                return value;
            } finally {
                this.releaseOwner(ownerUuid, owner);
            }
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    /** Waits for an in-flight operation, saves it, then releases its loaded data. */
    public void evict(UUID ownerUuid) throws IOException {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.rejectReentrantLifecycle();
        this.lifecycle.readLock().lock();
        try {
            this.ensureOpen();
            Owner owner = this.lockCurrentOwner(ownerUuid);
            try {
                this.flush(owner);
                owner.evictRequested = true;
            } finally {
                this.releaseOwner(ownerUuid, owner);
            }
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    public void flush() throws IOException {
        this.rejectReentrantLifecycle();
        this.lifecycle.writeLock().lock();
        try {
            this.ensureOpen();
            this.flushOwners();
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    /** A failed flush keeps the stores open and loaded so shutdown can be retried. */
    @Override
    public void close() throws IOException {
        this.rejectReentrantLifecycle();
        this.lifecycle.writeLock().lock();
        try {
            if (this.closed) return;
            this.flushOwners();
            this.owners.clear();
            this.closed = true;
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    private Owner lockCurrentOwner(UUID ownerUuid) {
        Owner owner = this.owners.compute(ownerUuid, (ignored, existing) -> {
            Owner current = existing == null ? new Owner() : existing;
            current.borrowers++;
            return current;
        });
        owner.gate.lock();
        return owner;
    }

    private void releaseOwner(UUID ownerUuid, Owner owner) {
        try {
            this.owners.compute(ownerUuid, (ignored, current) -> {
                if (current != owner) throw new IllegalStateException("Detached private owner");
                owner.borrowers--;
                // Queued operations are borrowers too. Keep their manager attached until all
                // have saved, and retain dirty data if any persistence attempt failed.
                return owner.borrowers == 0 && owner.evictRequested
                        && (owner.files == null || !owner.files.dirty) ? null : owner;
            });
        } finally {
            owner.gate.unlock();
        }
    }

    private void flushOwners() throws IOException {
        IOException failure = null;
        for (Owner owner : this.owners.values()) {
            try {
                this.flush(owner);
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private void flush(Owner owner) throws IOException {
        owner.gate.lock();
        try {
            if (owner.files == null || !owner.files.dirty) return;
            for (WaypointFileManager file : owner.files.getFileManagerMap().values()) {
                owner.files.saveWaypointFile(file);
            }
            owner.files.dirty = false;
        } finally {
            owner.gate.unlock();
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Private waypoint store is closed");
    }

    private void rejectReentrantLifecycle() {
        if (this.lifecycle.getReadHoldCount() != 0) {
            throw new IllegalStateException("Cannot change private storage lifecycle from an owner operation");
        }
    }

    public enum Quota { WAYPOINTS, LISTS }

    public static final class QuotaExceededException extends RuntimeException {
        private final Quota quota;

        private QuotaExceededException(Quota quota) {
            super("Private waypoint quota exceeded: " + quota);
            this.quota = quota;
        }

        public Quota quota() {
            return this.quota;
        }
    }

    private static final class Owner {
        private final ReentrantLock gate = new ReentrantLock(true);
        private OwnerFiles files;
        private int borrowers;
        private boolean evictRequested;
    }

    private final class OwnerFiles extends WaypointFilesManagerCore {
        private final UUID ownerUuid;
        private final Owner owner;
        private boolean dirty;

        private OwnerFiles(UUID ownerUuid, Owner owner) {
            super(PrivateWaypointStore.this.root.resolve(ownerUuid.toString()));
            this.ownerUuid = ownerUuid;
            this.owner = owner;
        }

        private void load() throws IOException {
            this.initOrReadCurrentWaypointFiles();
        }

        private void requireOwnerGate() {
            if (!this.owner.gate.isHeldByCurrentThread()
                    || PrivateWaypointStore.this.owners.get(this.ownerUuid) != this.owner) {
                throw new IllegalStateException("Private mutations require an active owner operation");
            }
        }

        @Override
        protected <T> T mutateDimension(String dimension, Supplier<T> action) {
            this.requireOwnerGate();
            try {
                return super.mutateDimension(dimension, action);
            } finally {
                this.dirty = true;
            }
        }

        @Override
        protected <T> T mutateDimension(String dimension, Supplier<T> action, Consumer<T> resultAction) {
            this.requireOwnerGate();
            try {
                return super.mutateDimension(dimension, action, resultAction);
            } finally {
                this.dirty = true;
            }
        }

        @Override
        public AddWaypointResult addWaypoint(String dimension, String listName, String displayName,
                                              SimpleWaypoint waypoint, Consumer<AddWaypointResult> callback) {
            this.requireOwnerGate();
            WaypointList list = this.list(dimension, listName);
            if (list == null || list.getWaypointByName(waypoint.name()) == null) {
                this.checkGrowth(true, list == null);
            }
            return super.addWaypoint(dimension, listName, displayName, waypoint, callback);
        }

        @Override
        public AddWaypointListResult addWaypointList(String dimension, String name, String displayName,
                                                      Consumer<AddWaypointListResult> callback) {
            this.requireOwnerGate();
            if (this.list(dimension, name) == null) this.checkGrowth(false, true);
            return super.addWaypointList(dimension, name, displayName, callback);
        }

        @Override
        public RestoreWaypointResult restoreWaypoint(String dimension, String listName, SimpleWaypoint waypoint,
                                                       Consumer<RestoreWaypointResult> callback) {
            this.requireOwnerGate();
            WaypointList list = this.list(dimension, listName);
            if (list != null && list.getWaypointByName(waypoint.name()) == null) this.checkGrowth(true, false);
            return super.restoreWaypoint(dimension, listName, waypoint, callback);
        }

        private WaypointList list(String dimension, String listName) {
            WaypointFileManager file = this.getWaypointFileManager(dimension);
            return file == null ? null : file.getWaypointListByName(listName);
        }

        private void checkGrowth(boolean waypointAdded, boolean listAdded) {
            PrivateWaypointConfig config = Objects.requireNonNull(PrivateWaypointStore.this.limits.get(), "limits");
            config.validate();
            long waypoints = 0;
            long lists = 0;
            for (WaypointFileManager file : this.getFileManagerMap().values()) {
                List<WaypointList> dimensionLists = file.getWaypointLists();
                lists += dimensionLists.size();
                for (WaypointList list : dimensionLists) waypoints += list.size();
            }
            if (waypointAdded && config.maxWaypointsPerPlayer() != 0 && waypoints >= config.maxWaypointsPerPlayer()) {
                throw new QuotaExceededException(Quota.WAYPOINTS);
            }
            if (listAdded && config.maxListsPerPlayer() != 0 && lists >= config.maxListsPerPlayer()) {
                throw new QuotaExceededException(Quota.LISTS);
            }
        }
    }
}
