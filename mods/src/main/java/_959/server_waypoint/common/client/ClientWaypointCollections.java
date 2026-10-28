package _959.server_waypoint.common.client;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.WaypointScopedMessage;
import _959.server_waypoint.core.network.DimensionSyncIdentifier;
import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import _959.server_waypoint.core.network.data.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.waypoint.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Client-thread collection bindings. Private cache files are account/address/backend isolated and
 * become presentable only after authorization and a current snapshot. Local persistence is supplied
 * by the profile owner; server synchronization never touches it.
 */
public final class ClientWaypointCollections {
    public enum State { UNAVAILABLE, LOADING, DENIED, READY }
    private final EnumMap<ClientWaypointSource, WaypointFilesManagerCore> stores = new EnumMap<>(ClientWaypointSource.class);
    private final EnumMap<ClientWaypointSource, ClientSynchronizationTracker> trackers = new EnumMap<>(ClientWaypointSource.class);
    private final EnumMap<ClientWaypointSource, State> states = new EnumMap<>(ClientWaypointSource.class);
    private final Map<EditRequestKey, ClientWaypointSelection> pendingEdits = new HashMap<>();
    private volatile long generation;
    private UUID account;
    private String address;
    private Integer backend;
    private UUID profile;
    private boolean privateReadable;

    public ClientWaypointCollections(WaypointFilesManagerCore shared) {
        stores.put(ClientWaypointSource.SHARED, Objects.requireNonNull(shared));
        stores.put(ClientWaypointSource.PRIVATE, new WaypointFilesManagerCore());
        for (ClientWaypointSource source : ClientWaypointSource.values()) {
            trackers.put(source, new ClientSynchronizationTracker());
            states.put(source, State.UNAVAILABLE);
        }
    }

    public long generation() { return generation; }
    public State state(ClientWaypointSource source) { return states.get(source); }
    public boolean ready(ClientWaypointSource source) { return state(source) == State.READY; }
    public WaypointFilesManagerCore files(ClientWaypointSource source) {
        return Objects.requireNonNull(stores.get(source), "Collection is not bound");
    }
    public ClientSynchronizationTracker tracker(ClientWaypointSource source) { return trackers.get(source); }

    public void beginConnection(UUID account, String address) {
        disconnect();
        this.account = Objects.requireNonNull(account);
        this.address = Objects.requireNonNull(address);
        states.put(ClientWaypointSource.SHARED, State.LOADING);
        states.put(ClientWaypointSource.PRIVATE, State.LOADING);
    }

    public void bindServer(Path gameRoot, int backend) {
        if (account == null) throw new IllegalStateException("No connection");
        if (Objects.equals(this.backend, backend)) return;
        invalidateServer();
        this.backend = backend;
        Path path = gameRoot.resolve("server_waypoint/private-cache").resolve(account.toString())
                .resolve(addressDigest(address)).resolve(Integer.toString(backend));
        var privateFiles = new CacheFiles(path);
        try {
            privateFiles.load();
        } catch (IOException | RuntimeException exception) {
            // This cache is expendable. Request a full authoritative snapshot after a failed read.
            privateFiles.clearWaypointFileManagers();
            WaypointClientMod.LOGGER.warn("Cannot load private client cache; requesting server data", exception);
        }
        // Cached revisions can reduce synchronization, but disk data never grants read authorization.
        stores.put(ClientWaypointSource.PRIVATE, privateFiles);
        states.put(ClientWaypointSource.PRIVATE, State.LOADING);
    }

    public void bindLocal(UUID profile, WaypointFilesManagerCore local) {
        generation++;
        pendingEdits.clear();
        this.profile = Objects.requireNonNull(profile);
        stores.put(ClientWaypointSource.LOCAL, Objects.requireNonNull(local));
        states.put(ClientWaypointSource.LOCAL, State.READY);
    }

    public void unbindLocal() {
        if (stores.remove(ClientWaypointSource.LOCAL) != null) {
            generation++;
            pendingEdits.clear();
        }
        profile = null;
        states.put(ClientWaypointSource.LOCAL, State.UNAVAILABLE);
    }

    /** Protocol rejection must not disable the independent Local profile. */
    public void serverUnavailable() {
        invalidateServer();
        backend = null;
    }

    public void disconnect() {
        unbindLocal();
        invalidateServer();
        account = null;
        address = null;
        backend = null;
    }

    private void invalidateServer() {
        generation++;
        privateReadable = false;
        pendingEdits.clear();
        stores.put(ClientWaypointSource.PRIVATE, new WaypointFilesManagerCore());
        for (ClientWaypointSource source : List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE)) {
            trackers.get(source).clearAll();
            states.put(source, State.UNAVAILABLE);
        }
    }

    public ClientWaypointSelection selection(ClientWaypointSource source, String dimension, String list, String waypoint) {
        return new ClientWaypointSelection(source, generation, source == ClientWaypointSource.LOCAL ? profile : null,
                dimension, list, waypoint);
    }

    public boolean valid(ClientWaypointSelection selection) {
        return selection.generation() == generation && ready(selection.source())
                && (selection.source() != ClientWaypointSource.LOCAL || Objects.equals(profile, selection.profileId()));
    }

    public void trackEdit(long requestId, ClientWaypointSelection selection) {
        if (!valid(selection)) throw new IllegalStateException("Obsolete selection");
        pendingEdits.put(new EditRequestKey(selection.source(), requestId), selection);
    }

    public boolean acceptResult(WaypointEditResultMessage result, long receivedGeneration) {
        if (receivedGeneration != generation) return false;
        EditRequestKey key = new EditRequestKey(ClientWaypointSource.fromScope(result.scope()), result.requestId());
        ClientWaypointSelection selection = pendingEdits.get(key);
        if (selection == null || selection.source() != ClientWaypointSource.fromScope(result.scope())
                || !valid(selection) || !selection.dimensionName().equals(result.dimensionName())
                || !selection.listIdentifier().equals(result.listIdentifier())
                || !Objects.equals(selection.waypointIdentifier(), result.previousWaypointIdentifier())) return false;
        pendingEdits.remove(key);
        return true;
    }

    public ClientUpdateRequestMessage updateRequest(ClientWaypointSource source) {
        if (source == ClientWaypointSource.LOCAL) throw new IllegalArgumentException("Local cannot request server data");
        var dimensions = new ArrayList<DimensionSyncIdentifier>();
        files(source).getFileManagerMap().forEach((dimension, manager) -> dimensions.add(new DimensionSyncIdentifier(dimension,
                manager.getWaypointLists().stream().map(list -> new WaypointListSyncIdentifier(list.name(), list.getSyncNum())).toList())));
        return new ClientUpdateRequestMessage(source.serverScope(), dimensions);
    }

    public void markSnapshotApplied(ClientWaypointSource source) {
        states.put(source, State.READY);
    }

    public boolean apply(WaypointScopedMessage message, long receivedGeneration) throws IOException {
        if (receivedGeneration != generation) return false;
        ClientWaypointSource source = ClientWaypointSource.fromScope(message.scope());
        if (message instanceof WaypointCollectionStateMessage state) {
            if (source == ClientWaypointSource.PRIVATE) privateReadable = state.readable();
            if (!state.readable()) {
                files(source).clearWaypointFileManagers();
                trackers.get(source).clearAll();
                pendingEdits.entrySet().removeIf(entry -> entry.getValue().source() == source);
                states.put(source, State.DENIED);
            } else if (!ready(source)) states.put(source, State.LOADING);
            return true;
        }
        if (source == ClientWaypointSource.PRIVATE && (!privateReadable || account == null || backend == null)) return false;
        WaypointFilesManagerCore files = files(source);
        ClientSynchronizationTracker tracker = trackers.get(source);
        if (message instanceof WaypointData data) {
            if (data.type() == WaypointData.Type.UPLOAD || data.type() == WaypointData.Type.WAYPOINT) return false;
            if (data.type() == WaypointData.Type.WORLD) {
                Set<String> remaining = new HashSet<>(files.getFileManagerMap().keySet());
                var replacement = new LinkedHashMap<String, List<WaypointList>>();
                data.dimensions().forEach(dimension -> { replacement.put(dimension.dimensionName(), dimension.waypointLists()); remaining.remove(dimension.dimensionName()); });
                remaining.forEach(dimension -> files.removeWaypointFileManager(dimension, true));
                files.replaceWaypointData(replacement);
            } else {
                for (DimensionWaypointData dimension : data.dimensions()) {
                    String name = dimension.dimensionName();
                    if (data.type() == WaypointData.Type.DIMENSION || dimension.waypointLists().isEmpty()) {
                        files.removeWaypointFileManager(name, true);
                    }
                    for (WaypointList list : dimension.waypointLists()) {
                        if (list.getSyncNum() == WaypointList.REMOVE_LIST) files.removeWaypointListImmediately(name, list.name());
                        else files.putWaypointList(name, list);
                    }
                    if (data.type() == WaypointData.Type.WAYPOINT_LIST) tracker.clearList(name, dimension.waypointLists().get(0).name());
                    else tracker.clearDimension(name);
                }
            }
            if (data.type() == WaypointData.Type.WORLD || data.type() == WaypointData.Type.UPDATES) {
                tracker.clearAll();
                markSnapshotApplied(source);
            }
        } else if (message instanceof WaypointModificationMessage delta) {
            if (!ready(source)) return false;
            var manager = files.getWaypointFileManager(delta.dimensionName());
            var list = manager == null ? null : manager.getWaypointListByName(delta.listName());
            if (list == null) {
                boolean creation = delta.type() == WaypointModificationType.ADD_LIST && delta.syncId() == WaypointList.SERVER_N
                        || delta.type() == WaypointModificationType.ADD && delta.syncId() == WaypointList.SERVER_N + 1;
                if (!creation || tracker.isOutOfSync(delta.dimensionName(), delta.listName())) {
                    if (delta.type() != WaypointModificationType.REMOVE_LIST) tracker.markOutOfSync(delta.dimensionName(), delta.listName());
                    return false;
                }
            } else if (!tracker.shouldApplyIncremental(delta.dimensionName(), delta.listName(), list.getSyncNum(), delta.syncId())) return false;
            switch (delta.type()) {
                case ADD -> files.addWaypointFromRemoteServer(delta.dimensionName(), delta.listName(), delta.listDisplayName(), delta.waypoint(), delta.syncId());
                case UPDATE -> files.updateWaypointFromRemoteServer(delta.dimensionName(), delta.listName(), delta.waypointName(), delta.waypoint(), delta.syncId());
                case REMOVE -> files.removeWaypointFromRemoteServer(delta.dimensionName(), delta.listName(), delta.waypointName(), delta.syncId());
                case ADD_LIST -> files.putWaypointList(delta.dimensionName(), WaypointList.buildByServer(delta.listName(), delta.listDisplayName()));
                case REMOVE_LIST -> files.removeWaypointListImmediately(delta.dimensionName(), delta.listName());
            }
        } else if (message instanceof WaypointListUpdateMessage update) {
            if (!ready(source)) return false;
            var manager = files.getWaypointFileManager(update.dimensionName());
            var before = manager == null ? null : manager.getWaypointListByName(update.previousListIdentifier());
            var current = before != null ? before : manager == null ? null : manager.getWaypointListByName(update.waypointList().name());
            boolean recovery = tracker.isOutOfSync(update.dimensionName(), update.previousListIdentifier())
                    || tracker.isOutOfSync(update.dimensionName(), update.waypointList().name());
            if (!recovery && current != null && update.waypointList().getSyncNum() <= current.getSyncNum()) return false;
            files.removeWaypointListImmediately(update.dimensionName(), update.previousListIdentifier());
            files.putWaypointList(update.dimensionName(), update.waypointList());
            tracker.clearList(update.dimensionName(), update.previousListIdentifier());
            tracker.clearList(update.dimensionName(), update.waypointList().name());
        } else if (message instanceof WaypointEditResultMessage result) {
            return acceptResult(result, receivedGeneration);
        } else return false;
        files.saveAllWaypointFiles();
        return true;
    }

    private record EditRequestKey(ClientWaypointSource source, long requestId) { }

    private static final class CacheFiles extends WaypointFilesManagerCore {
        private CacheFiles(Path directory) { super(directory); }
        private void load() throws IOException { initOrReadCurrentWaypointFiles(); }
    }

    private static String addressDigest(String address) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
