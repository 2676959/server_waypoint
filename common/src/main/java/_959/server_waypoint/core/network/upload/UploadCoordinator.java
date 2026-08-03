package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.TextButton;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * Correlates a player-issued upload command with the C2S response and merges the
 * validated data into the server-owned waypoint files.
 */
public final class UploadCoordinator<P> {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_WAYPOINTS_PER_REQUEST = 4_096;
    private static final int MAX_ABSOLUTE_COORDINATE = 30_000_000;
    private static final int MIN_Y = -2_048;
    private static final int MAX_Y = 4_096;

    private final WaypointServerCore waypointServer;
    private final PlayerMessageSender<P> playerMessageSender;
    private final Consumer<MessageBuffer> packetBroadcaster;
    private final Predicate<P> permissionChecker;
    private final Predicate<P> deletePermissionChecker;
    private final Map<P, PendingUpload> pendingUploads = new ConcurrentHashMap<>();

    public UploadCoordinator(
            WaypointServerCore waypointServer,
            PlayerMessageSender<P> playerMessageSender,
            Consumer<MessageBuffer> packetBroadcaster,
            Predicate<P> permissionChecker,
            Predicate<P> deletePermissionChecker
    ) {
        this.waypointServer = waypointServer;
        this.playerMessageSender = playerMessageSender;
        this.packetBroadcaster = packetBroadcaster;
        this.permissionChecker = permissionChecker;
        this.deletePermissionChecker = deletePermissionChecker;
    }

    public UploadRequestBuffer begin(P player, UploadScope scope, UploadConflictPolicy conflictPolicy, boolean deleteMissing,
                                     List<String> dimensionNames, String listName, String waypointName) {
        UploadRequestBuffer request = new UploadRequestBuffer(UUID.randomUUID(), scope, conflictPolicy, deleteMissing,
                dimensionNames, listName, waypointName);
        this.pendingUploads.put(player, new PendingUpload(request, Instant.now().plus(REQUEST_TIMEOUT)));
        return request;
    }

    public void onUploadChunk(P player, UploadChunkBuffer chunk) {
        PendingUpload pending = this.pendingUploads.get(player);
        if (pending == null || !pending.request.requestId().equals(chunk.requestId())) {
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));
            return;
        }
        if (pending.expiresAt.isBefore(Instant.now())) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.expired"));
            return;
        }
        if (!this.permissionChecker.test(player)) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.permission.revoked"));
            return;
        }
        if (pending.request.deleteMissing() && !this.deletePermissionChecker.test(player)) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.delete.permission.revoked"));
            return;
        }
        if (chunk.sequence() != pending.nextSequence) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));
            return;
        }
        pending.nextSequence++;

        if (chunk.status() != UploadStatus.SUCCESS) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, switch (chunk.status()) {
                case XAERO_NOT_INSTALLED -> translatable("waypoint.upload.xaero.missing");
                case XAERO_NOT_READY -> translatable("waypoint.upload.xaero.not-ready");
                case FAILED -> translatable("waypoint.upload.client.failed");
                case SUCCESS -> throw new IllegalStateException("Handled above");
            });
            return;
        }

        try {
            appendChunk(pending, chunk);
        } catch (IllegalArgumentException e) {
            this.pendingUploads.remove(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));
            return;
        }

        if (!chunk.finalChunk()) {
            return;
        }

        this.pendingUploads.remove(player, pending);
        MergeSummary summary = merge(pending);
        for (DimensionWaypointBuffer update : summary.dimensionUpdates) {
            this.packetBroadcaster.accept(update);
        }
        this.playerMessageSender.send(player, translatable(
                "waypoint.upload.complete",
                text(summary.added), text(summary.replaced), text(summary.deleted),
                text(summary.unchanged), text(summary.conflicts), text(summary.skipped)
        ));
        this.playerMessageSender.send(player, translatable("waypoint.upload.legend"));
        if (summary.conflicts > 0 && pending.request.conflictPolicy() == UploadConflictPolicy.SERVER) {
            this.playerMessageSender.send(player, translatable(
                    "waypoint.upload.conflicts.server-kept",
                    text(summary.conflicts),
                    TextButton.uploadPreferLocalButton(pending.request)
            ));
        }
        if (summary.saveFailed) {
            this.playerMessageSender.send(player, translatable("waypoint.upload.save.failed"));
        }
    }

    private static void appendChunk(PendingUpload pending, UploadChunkBuffer chunk) {
        for (UploadedWaypointListChunk listChunk : chunk.waypointLists()) {
            if (!pending.request.dimensionNames().contains(listChunk.dimensionName())
                    || (pending.request.listName() != null && !pending.request.listName().equals(listChunk.listName()))) {
                throw new IllegalArgumentException("Upload response exceeds requested scope");
            }
            if (!isValidText(listChunk.dimensionName(), false) || !isValidText(listChunk.listName(), false)) {
                throw new IllegalArgumentException("Invalid uploaded waypoint list name");
            }
            List<SimpleWaypoint> target = pending.uploadedWaypoints.computeIfAbsent(
                    new UploadListKey(listChunk.dimensionName(), listChunk.listName()),
                    ignored -> new ArrayList<>()
            );
            for (SimpleWaypoint waypoint : listChunk.waypoints()) {
                if (pending.request.waypointName() != null && !pending.request.waypointName().equals(waypoint.name())) {
                    throw new IllegalArgumentException("Upload response exceeds requested waypoint scope");
                }
                if (++pending.waypointCount > MAX_WAYPOINTS_PER_REQUEST) {
                    throw new IllegalArgumentException("Upload exceeds waypoint limit");
                }
                target.add(new SimpleWaypoint(waypoint));
            }
        }
    }

    private MergeSummary merge(PendingUpload pending) {
        Map<String, List<WaypointList>> changedListsByDimension = new LinkedHashMap<>();
        Map<String, WaypointFileManager> changedManagers = new LinkedHashMap<>();
        MergeSummary summary = new MergeSummary();

        for (Map.Entry<UploadListKey, List<SimpleWaypoint>> entry : pending.uploadedWaypoints.entrySet()) {
            UploadListKey key = entry.getKey();
            WaypointFileManager fileManager = this.waypointServer.getOrCreateWaypointFileManager(key.dimensionName);
            WaypointList waypointList = fileManager.getWaypointListByName(key.listName);
            boolean changed = false;
            boolean invalidWaypointFound = false;

            for (SimpleWaypoint waypoint : entry.getValue()) {
                if (!isValidWaypoint(waypoint)) {
                    summary.skipped++;
                    invalidWaypointFound = true;
                    continue;
                }
                if (waypointList == null) {
                    waypointList = WaypointList.buildByServer(key.listName);
                    fileManager.addWaypointList(waypointList);
                    changed = true;
                }
                SimpleWaypoint existing = waypointList.getWaypointByName(waypoint.name());
                if (existing == null) {
                    waypointList.addByServer(waypoint);
                    summary.added++;
                    changed = true;
                } else if (existing.compareProperties(
                        waypoint.name(), waypoint.initials(), waypoint.pos(), waypoint.rgb(), waypoint.yaw(), waypoint.global())) {
                    summary.unchanged++;
                } else if (pending.request.conflictPolicy() == UploadConflictPolicy.LOCAL) {
                    existing.copyFrom(waypoint);
                    waypointList.incSyncNum();
                    summary.replaced++;
                    changed = true;
                } else {
                    summary.conflicts++;
                }
            }

            if (pending.request.deleteMissing() && waypointList != null && !invalidWaypointFound) {
                Set<String> localWaypointNames = new HashSet<>();
                for (SimpleWaypoint waypoint : entry.getValue()) {
                    if (waypoint != null) {
                        localWaypointNames.add(waypoint.name());
                    }
                }
                for (SimpleWaypoint existing : new ArrayList<>(waypointList.simpleWaypoints())) {
                    if (!localWaypointNames.contains(existing.name())) {
                        waypointList.remove(existing);
                        summary.deleted++;
                        changed = true;
                    }
                }
            }

            if (changed) {
                markChangedList(changedManagers, changedListsByDimension, key.dimensionName, fileManager, waypointList);
            }
        }

        if (pending.request.deleteMissing()) {
            deleteMissingServerWaypoints(pending, changedManagers, changedListsByDimension, summary);
        }

        for (Map.Entry<String, WaypointFileManager> entry : changedManagers.entrySet()) {
            try {
                entry.getValue().saveDimension();
            } catch (IOException e) {
                WaypointServerCore.LOGGER.error("Failed to save uploaded waypoints for dimension {}", entry.getKey(), e);
                summary.saveFailed = true;
            }
        }
        for (Map.Entry<String, List<WaypointList>> entry : changedListsByDimension.entrySet()) {
            summary.dimensionUpdates.add(new DimensionWaypointBuffer(entry.getKey(), entry.getValue()));
        }
        return summary;
    }

    private void deleteMissingServerWaypoints(PendingUpload pending,
                                              Map<String, WaypointFileManager> changedManagers,
                                              Map<String, List<WaypointList>> changedListsByDimension,
                                              MergeSummary summary) {
        switch (pending.request.scope()) {
            case WORLD, DIMENSION -> {
                for (String dimensionName : pending.request.dimensionNames()) {
                    WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
                    if (fileManager == null) {
                        continue;
                    }
                    Set<String> localListNames = getUploadedListNames(pending, dimensionName);
                    for (String serverListName : new ArrayList<>(fileManager.getWaypointListMap().keySet())) {
                        if (!localListNames.contains(serverListName)) {
                            removeServerList(dimensionName, fileManager, serverListName,
                                    changedManagers, changedListsByDimension, summary);
                        }
                    }
                }
            }
            case LIST -> {
                String dimensionName = pending.request.dimensionNames().get(0);
                if (!pending.uploadedWaypoints.containsKey(new UploadListKey(dimensionName, pending.request.listName()))) {
                    WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
                    if (fileManager != null) {
                        removeServerList(dimensionName, fileManager, pending.request.listName(),
                                changedManagers, changedListsByDimension, summary);
                    }
                }
            }
            case WAYPOINT -> {
                String dimensionName = pending.request.dimensionNames().get(0);
                WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
                if (fileManager == null) {
                    return;
                }
                WaypointList waypointList = fileManager.getWaypointListByName(pending.request.listName());
                if (waypointList == null) {
                    return;
                }
                List<SimpleWaypoint> uploadedWaypoints = pending.uploadedWaypoints.get(
                        new UploadListKey(dimensionName, pending.request.listName())
                );
                boolean localWaypointExists = uploadedWaypoints != null && uploadedWaypoints.stream()
                        .anyMatch(waypoint -> waypoint != null && pending.request.waypointName().equals(waypoint.name()));
                if (!localWaypointExists) {
                    SimpleWaypoint serverWaypoint = waypointList.getWaypointByName(pending.request.waypointName());
                    if (serverWaypoint != null) {
                        waypointList.remove(serverWaypoint);
                        summary.deleted++;
                        markChangedList(changedManagers, changedListsByDimension, dimensionName, fileManager, waypointList);
                    }
                }
            }
        }
    }

    private static Set<String> getUploadedListNames(PendingUpload pending, String dimensionName) {
        Set<String> listNames = new HashSet<>();
        for (UploadListKey key : pending.uploadedWaypoints.keySet()) {
            if (dimensionName.equals(key.dimensionName)) {
                listNames.add(key.listName);
            }
        }
        return listNames;
    }

    private static void removeServerList(String dimensionName, WaypointFileManager fileManager, String listName,
                                         Map<String, WaypointFileManager> changedManagers,
                                         Map<String, List<WaypointList>> changedListsByDimension,
                                         MergeSummary summary) {
        WaypointList serverList = fileManager.getWaypointListByName(listName);
        if (serverList == null) {
            return;
        }
        summary.deleted += serverList.size();
        fileManager.removeWaypointListByName(listName);
        changedManagers.put(dimensionName, fileManager);
        changedListsByDimension.computeIfAbsent(dimensionName, ignored -> new ArrayList<>())
                .add(WaypointList.build(listName, WaypointList.REMOVE_LIST));
    }

    private static void markChangedList(Map<String, WaypointFileManager> changedManagers,
                                        Map<String, List<WaypointList>> changedListsByDimension,
                                        String dimensionName, WaypointFileManager fileManager, WaypointList waypointList) {
        changedManagers.put(dimensionName, fileManager);
        changedListsByDimension.computeIfAbsent(dimensionName, ignored -> new ArrayList<>()).add(waypointList);
    }

    private static boolean isValidWaypoint(SimpleWaypoint waypoint) {
        if (waypoint == null || waypoint.pos() == null
                || !isValidText(waypoint.name(), false)
                || !isValidText(waypoint.initials(), true)) {
            return false;
        }
        int x = waypoint.pos().x();
        int y = waypoint.pos().y();
        int z = waypoint.pos().z();
        return Math.abs((long) x) <= MAX_ABSOLUTE_COORDINATE
                && Math.abs((long) z) <= MAX_ABSOLUTE_COORDINATE
                && y >= MIN_Y
                && y <= MAX_Y;
    }

    private static boolean isValidText(String value, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty())
                || value.getBytes(StandardCharsets.UTF_8).length > 1_024) {
            return false;
        }
        return value.chars().noneMatch(Character::isISOControl);
    }

    @FunctionalInterface
    public interface PlayerMessageSender<P> {
        void send(P player, Component message);
    }

    private static final class PendingUpload {
        private final UploadRequestBuffer request;
        private final Instant expiresAt;
        private final Map<UploadListKey, List<SimpleWaypoint>> uploadedWaypoints = new LinkedHashMap<>();
        private int nextSequence;
        private int waypointCount;

        private PendingUpload(UploadRequestBuffer request, Instant expiresAt) {
            this.request = request;
            this.expiresAt = expiresAt;
        }
    }

    private record UploadListKey(String dimensionName, String listName) {
    }

    private static final class MergeSummary {
        private int added;
        private int replaced;
        private int deleted;
        private int unchanged;
        private int conflicts;
        private int skipped;
        private boolean saveFailed;
        private final List<DimensionWaypointBuffer> dimensionUpdates = new ArrayList<>();
    }
}
