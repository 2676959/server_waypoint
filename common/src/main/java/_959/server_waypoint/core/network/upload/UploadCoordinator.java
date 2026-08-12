package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.text.TextButtonBuilder;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private static final int MAX_LISTS_PER_REQUEST = 1_024;
    private static final long MAX_RETAINED_BYTES_PER_REQUEST = 16L * 1_024L * 1_024L;
    private static final int MAX_ABSOLUTE_COORDINATE = 30_000_000;
    private static final int MIN_Y = -2_048;
    private static final int MAX_Y = 4_096;
    private static final ScheduledExecutorService EXPIRY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "server-waypoint-upload-expiry");
                thread.setDaemon(true);
                return thread;
            });

    private final WaypointServerCore waypointServer;
    private final PlayerMessageSender<P> playerMessageSender;
    private final Consumer<WaypointData> waypointDataBroadcaster;
    private final Predicate<P> permissionChecker;
    private final Predicate<P> deletePermissionChecker;
    private final NavigationService<P> navigationService;
    private final Map<P, PendingUpload> pendingUploads = new ConcurrentHashMap<>();

    public UploadCoordinator(
            WaypointServerCore waypointServer,
            PlayerMessageSender<P> playerMessageSender,
            Consumer<WaypointData> waypointDataBroadcaster,
            Predicate<P> permissionChecker,
            Predicate<P> deletePermissionChecker,
            NavigationService<P> navigationService
    ) {
        this.waypointServer = Objects.requireNonNull(waypointServer, "waypointServer");
        this.playerMessageSender = Objects.requireNonNull(playerMessageSender, "playerMessageSender");
        this.waypointDataBroadcaster = Objects.requireNonNull(waypointDataBroadcaster, "waypointDataBroadcaster");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker");
        this.deletePermissionChecker = Objects.requireNonNull(deletePermissionChecker, "deletePermissionChecker");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
    }

    public UploadRequestBuffer begin(P player, UploadScope scope, UploadConflictPolicy conflictPolicy, boolean deleteMissing,
                                     List<String> dimensionNames, String listName, String waypointName) {
        if (deleteMissing && conflictPolicy != UploadConflictPolicy.LOCAL) {
            throw new IllegalArgumentException("Only force-local uploads can delete missing waypoints");
        }
        UploadRequestBuffer request = new UploadRequestBuffer(
                UUID.randomUUID(), dimensionNames, listName, waypointName
        );
        Map<String, WaypointFilesManagerCore.DimensionRevision> revisions = new LinkedHashMap<>();
        for (String dimensionName : request.dimensionNames()) {
            revisions.putIfAbsent(
                    dimensionName,
                    this.waypointServer.captureDimensionRevision(dimensionName)
            );
        }
        PendingUpload pending = new PendingUpload(
                request,
                scope,
                conflictPolicy,
                deleteMissing,
                Instant.now().plus(REQUEST_TIMEOUT),
                revisions
        );
        PendingUpload replaced = this.pendingUploads.put(player, pending);
        if (replaced != null) {
            replaced.cancelExpiry();
        }
        pending.expiryTask = EXPIRY_EXECUTOR.schedule(
                () -> this.pendingUploads.remove(player, pending),
                REQUEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return request;
    }

    public void onDisconnect(P player) {
        PendingUpload pending = this.pendingUploads.remove(player);
        if (pending != null) {
            pending.cancelExpiry();
        }
    }

    public void onUpload(P player, WaypointData waypointData) {
        WaypointData.Upload upload = waypointData.uploadData();
        PendingUpload pending = this.pendingUploads.get(player);
        if (pending == null || !pending.request.requestId().equals(upload.requestId())) {
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));
            return;
        }
        if (pending.expiresAt.isBefore(Instant.now())) {
            this.removePending(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.expired"));
            return;
        }
        if (!this.permissionChecker.test(player)) {
            this.removePending(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.permission.revoked"));
            return;
        }
        if (pending.deleteMissing && !this.deletePermissionChecker.test(player)) {
            this.removePending(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.delete.permission.revoked"));
            return;
        }
        if (upload.status() != UploadStatus.SUCCESS) {
            this.removePending(player, pending);
            this.playerMessageSender.send(player, switch (upload.status()) {
                case XAERO_NOT_INSTALLED -> translatable("waypoint.upload.xaero.missing");
                case XAERO_NOT_READY -> translatable("waypoint.upload.xaero.not-ready");
                case FAILED -> translatable("waypoint.upload.client.failed");
                case SUCCESS -> throw new IllegalStateException("Handled above");
            });
            return;
        }

        try {
            appendUpload(pending, waypointData.dimensions());
        } catch (IllegalArgumentException exception) {
            this.removePending(player, pending);
            this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));
            return;
        }

        this.removePending(player, pending);
        MergeSummary summary;
        try {
            summary = this.merge(pending);
        } catch (MessageEncodingException exception) {
            WaypointServerCore.LOGGER.warn(
                    "Rejected waypoint upload because its update could not be encoded within the {}-byte logical-message budget",
                    ChunkedMessageManager.MAX_MESSAGE_BYTES,
                    exception
            );
            this.playerMessageSender.send(
                    player,
                    translatable("waypoint.network.encoding_failed")
            );
            return;
        } catch (RuntimeException exception) {
            WaypointServerCore.LOGGER.warn("Failed to apply waypoint upload", exception);
            this.playerMessageSender.send(player, translatable("waypoint.upload.client.failed"));
            return;
        }
        if (!summary.dimensionUpdates.isEmpty()) {
            this.waypointDataBroadcaster.accept(WaypointData.updates(summary.dimensionUpdates));
        }
        for (NavigationReplacement replacement : summary.navigationReplacements) {
            this.navigationService.refreshTarget(replacement.previous(), replacement.updated());
        }
        this.playerMessageSender.send(player, translatable(
                "waypoint.upload.complete",
                text(summary.added), text(summary.replaced), text(summary.deleted),
                text(summary.unchanged), text(summary.conflicts), text(summary.skipped)
        ));
        this.playerMessageSender.send(player, translatable("waypoint.upload.legend"));
        if (summary.conflicts > 0 && pending.conflictPolicy == UploadConflictPolicy.SERVER) {
            this.playerMessageSender.send(player, translatable(
                    "waypoint.upload.conflicts.server-kept",
                    text(summary.conflicts),
                    TextButtonBuilder.uploadPreferLocalButton(pending.scope, pending.request)
            ));
        }
        if (summary.staleDimensions > 0) {
            this.playerMessageSender.send(player, translatable(
                    "waypoint.upload.request.stale",
                    text(summary.staleDimensions)
            ));
        }
        if (summary.saveFailed) {
            this.playerMessageSender.send(player, translatable("waypoint.upload.save.failed"));
        }
    }

    private void removePending(P player, PendingUpload pending) {
        this.pendingUploads.remove(player, pending);
        pending.cancelExpiry();
    }

    private static void appendUpload(
            PendingUpload pending,
            List<DimensionWaypointData> uploadedDimensions
    ) {
        for (DimensionWaypointData dimension : uploadedDimensions) {
            if (!pending.request.dimensionNames().contains(dimension.dimensionName())
                    || !isValidText(dimension.dimensionName(), false)) {
                throw new IllegalArgumentException("Upload response exceeds requested dimension scope");
            }
            for (WaypointList waypointList : dimension.waypointLists()) {
                if (pending.request.listName() != null
                        && !pending.request.listName().equals(waypointList.name())) {
                    throw new IllegalArgumentException("Upload response exceeds requested waypoint-list scope");
                }
                if (!isValidText(waypointList.name(), true)) {
                    throw new IllegalArgumentException("Invalid uploaded waypoint list name");
                }
                pending.retainedBytes += utf8Length(dimension.dimensionName())
                        + utf8Length(waypointList.name());
                if (pending.retainedBytes > MAX_RETAINED_BYTES_PER_REQUEST) {
                    throw new IllegalArgumentException("Upload exceeds retained-byte limit");
                }
                UploadListKey key = new UploadListKey(dimension.dimensionName(), waypointList.name());
                if (pending.uploadedWaypoints.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate uploaded waypoint list");
                }
                if (pending.uploadedWaypoints.size() >= MAX_LISTS_PER_REQUEST) {
                    throw new IllegalArgumentException("Upload exceeds waypoint-list limit");
                }
                List<SimpleWaypoint> target = new ArrayList<>();
                pending.uploadedWaypoints.put(key, target);
                for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                    if (++pending.waypointCount > MAX_WAYPOINTS_PER_REQUEST) {
                        throw new IllegalArgumentException("Upload exceeds waypoint limit");
                    }
                    SimpleWaypoint sanitized = sanitizeUploadedWaypoint(waypoint);
                    if (pending.request.waypointName() != null
                            && (sanitized == null
                            || !pending.request.waypointName().equals(sanitized.name()))) {
                        throw new IllegalArgumentException("Upload response exceeds requested waypoint scope");
                    }
                    pending.retainedBytes += retainedBytes(sanitized);
                    if (pending.retainedBytes > MAX_RETAINED_BYTES_PER_REQUEST) {
                        throw new IllegalArgumentException("Upload exceeds retained-byte limit");
                    }
                    target.add(sanitized);
                }
            }
        }
    }

    private MergeSummary merge(PendingUpload pending) {
        MergeSummary summary = new MergeSummary();
        for (String dimensionName : new LinkedHashSet<>(pending.request.dimensionNames())) {
            WaypointFilesManagerCore.DimensionRevision expectedRevision =
                    pending.dimensionRevisions.get(dimensionName);
            if (expectedRevision == null) {
                summary.staleDimensions++;
                continue;
            }
            boolean hasUploadedLists = pending.uploadedWaypoints.keySet().stream()
                    .anyMatch(key -> dimensionName.equals(key.dimensionName));
            if (!expectedRevision.exists() && !hasUploadedLists) {
                continue;
            }
            WaypointFilesManagerCore.RevisionedDimensionMutationResult<DimensionMergeSummary> result =
                    this.waypointServer.applyDimensionMutationIfRevision(
                            dimensionName,
                            expectedRevision,
                            mutation -> {
                                DimensionMergeSummary dimensionSummary =
                                        mergeDimension(pending, dimensionName, mutation);
                                if (!dimensionSummary.listUpdates.isEmpty()) {
                                    ChunkedMessageManager.validateEncodable(WaypointData.updates(
                                            List.of(new DimensionWaypointData(
                                                    dimensionName,
                                                    List.copyOf(
                                                            dimensionSummary.listUpdates.values()
                                                    )
                                            ))
                                    ));
                                }
                                return dimensionSummary;
                            }
                    );
            if (result.status()
                    == WaypointFilesManagerCore.RevisionedDimensionMutationStatus.STALE_REVISION) {
                summary.staleDimensions++;
                continue;
            }
            DimensionMergeSummary dimensionSummary = Objects.requireNonNull(result.value());
            summary.add(dimensionSummary);
            summary.saveFailed |= result.saveFailed();
            if (!dimensionSummary.listUpdates.isEmpty()) {
                summary.dimensionUpdates.add(new DimensionWaypointData(
                        dimensionName,
                        List.copyOf(dimensionSummary.listUpdates.values())
                ));
            }
        }
        return summary;
    }

    private static DimensionMergeSummary mergeDimension(
            PendingUpload pending,
            String dimensionName,
            WaypointFileManager.AtomicMutation mutation
    ) {
        DimensionMergeSummary summary = new DimensionMergeSummary();
        for (Map.Entry<UploadListKey, List<SimpleWaypoint>> entry
                : pending.uploadedWaypoints.entrySet()) {
            UploadListKey key = entry.getKey();
            if (!dimensionName.equals(key.dimensionName)) {
                continue;
            }
            boolean invalidWaypointFound = false;
            for (SimpleWaypoint waypoint : entry.getValue()) {
                if (!isValidWaypoint(waypoint)) {
                    summary.skipped++;
                    invalidWaypointFound = true;
                    continue;
                }
                WaypointFilesManagerCore.AddWaypointResult addResult =
                        mutation.addWaypoint(key.listName, waypoint);
                if (addResult.status() == WaypointFilesManagerCore.AddWaypointStatus.ADDED) {
                    summary.added++;
                    summary.changedLists.add(key.listName);
                    continue;
                }

                SimpleWaypoint existing = addResult.waypointSnapshot();
                if (hasSameXaeroProperties(existing, waypoint)) {
                    summary.unchanged++;
                    continue;
                }
                if (pending.conflictPolicy != UploadConflictPolicy.LOCAL) {
                    summary.conflicts++;
                    continue;
                }

                SimpleWaypoint replacement = mergeXaeroProperties(existing, waypoint);
                WaypointFilesManagerCore.UpdateWaypointResult updateResult =
                        mutation.updateWaypoint(key.listName, existing.name(), replacement);
                if (updateResult.status() == WaypointFilesManagerCore.UpdateWaypointStatus.UPDATED) {
                    summary.replaced++;
                    summary.changedLists.add(key.listName);
                    WaypointList listSnapshot = Objects.requireNonNull(
                            mutation.waypointList(key.listName)
                    );
                    summary.navigationReplacements.add(new NavigationReplacement(
                            new NavigationTarget(
                                    dimensionName,
                                    listSnapshot,
                                    Objects.requireNonNull(updateResult.beforeSnapshot())
                            ),
                            new NavigationTarget(
                                    dimensionName,
                                    listSnapshot,
                                    Objects.requireNonNull(updateResult.afterSnapshot())
                            )
                    ));
                } else if (updateResult.status()
                        == WaypointFilesManagerCore.UpdateWaypointStatus.IDENTICAL) {
                    summary.unchanged++;
                } else {
                    summary.conflicts++;
                }
            }

            if (pending.deleteMissing
                    && pending.scope != UploadScope.WAYPOINT
                    && !invalidWaypointFound) {
                Set<String> localWaypointNames = new HashSet<>();
                for (SimpleWaypoint waypoint : entry.getValue()) {
                    if (waypoint != null) {
                        localWaypointNames.add(waypoint.name());
                    }
                }
                WaypointList waypointList = mutation.waypointList(key.listName);
                if (waypointList == null) {
                    continue;
                }
                for (SimpleWaypoint existing : waypointList.simpleWaypoints()) {
                    if (!localWaypointNames.contains(existing.name())) {
                        WaypointFilesManagerCore.RemoveWaypointResult removeResult =
                                mutation.removeWaypoint(key.listName, existing.name());
                        if (removeResult.status()
                                == WaypointFilesManagerCore.RemoveWaypointStatus.REMOVED) {
                            summary.deleted++;
                            summary.changedLists.add(key.listName);
                        }
                    }
                }
            }
        }

        if (pending.deleteMissing) {
            deleteMissingServerWaypoints(pending, dimensionName, mutation, summary);
        }

        for (String listName : summary.changedLists) {
            if (summary.removedLists.contains(listName)) {
                summary.listUpdates.put(listName, WaypointList.build(listName, WaypointList.REMOVE_LIST));
                continue;
            }
            WaypointList waypointList = mutation.waypointList(listName);
            if (waypointList != null) {
                summary.listUpdates.put(listName, waypointList);
            }
        }
        return summary;
    }

    private static void deleteMissingServerWaypoints(
            PendingUpload pending,
            String dimensionName,
            WaypointFileManager.AtomicMutation mutation,
            DimensionMergeSummary summary
    ) {
        switch (pending.scope) {
            case WORLD, DIMENSION -> {
                Set<String> localListNames = getUploadedListNames(pending, dimensionName);
                for (WaypointList serverList : mutation.waypointLists()) {
                    if (!localListNames.contains(serverList.name())) {
                        removeServerList(mutation, serverList.name(), summary);
                    }
                }
            }
            case LIST -> {
                if (!pending.uploadedWaypoints.containsKey(
                        new UploadListKey(dimensionName, pending.request.listName())
                )) {
                    removeServerList(mutation, pending.request.listName(), summary);
                }
            }
            case WAYPOINT -> {
                List<SimpleWaypoint> uploadedWaypoints = pending.uploadedWaypoints.get(
                        new UploadListKey(dimensionName, pending.request.listName())
                );
                boolean localWaypointExists = uploadedWaypoints != null
                        && uploadedWaypoints.stream().anyMatch(waypoint -> waypoint != null
                        && pending.request.waypointName().equals(waypoint.name()));
                if (!localWaypointExists) {
                    WaypointFilesManagerCore.RemoveWaypointResult removeResult =
                            mutation.removeWaypoint(
                                    pending.request.listName(),
                                    pending.request.waypointName()
                            );
                    if (removeResult.status()
                            == WaypointFilesManagerCore.RemoveWaypointStatus.REMOVED) {
                        summary.deleted++;
                        summary.changedLists.add(pending.request.listName());
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

    private static void removeServerList(
            WaypointFileManager.AtomicMutation mutation,
            String listName,
            DimensionMergeSummary summary
    ) {
        WaypointList removedList = mutation.removeWaypointList(listName);
        if (removedList == null) {
            return;
        }
        summary.deleted += removedList.size();
        summary.changedLists.add(listName);
        summary.removedLists.add(listName);
    }

    static boolean hasSameXaeroProperties(SimpleWaypoint serverWaypoint, SimpleWaypoint uploadedWaypoint) {
        return serverWaypoint.name().equals(uploadedWaypoint.name())
                && serverWaypoint.initials().equals(uploadedWaypoint.initials())
                && serverWaypoint.pos().equals(uploadedWaypoint.pos())
                && serverWaypoint.rgb() == uploadedWaypoint.rgb()
                && serverWaypoint.yaw() == uploadedWaypoint.yaw()
                && serverWaypoint.global() == uploadedWaypoint.global();
    }

    static SimpleWaypoint mergeXaeroProperties(SimpleWaypoint serverWaypoint, SimpleWaypoint uploadedWaypoint) {
        return new SimpleWaypoint(
                uploadedWaypoint.name(),
                serverWaypoint.displayName(),
                uploadedWaypoint.initials(),
                uploadedWaypoint.pos(),
                uploadedWaypoint.rgb(),
                uploadedWaypoint.yaw(),
                uploadedWaypoint.global(),
                serverWaypoint.keywords(),
                serverWaypoint.description()
        );
    }

    static @org.jetbrains.annotations.Nullable SimpleWaypoint sanitizeUploadedWaypoint(
            SimpleWaypoint waypoint
    ) {
        if (waypoint == null) {
            return null;
        }
        try {
            return new SimpleWaypoint(
                    waypoint.name(),
                    waypoint.initials(),
                    waypoint.pos(),
                    waypoint.rgb(),
                    waypoint.yaw(),
                    waypoint.global()
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isValidWaypoint(SimpleWaypoint waypoint) {
        if (waypoint == null || waypoint.pos() == null
                || !isValidText(waypoint.name(), true)
                || !isValidText(waypoint.initials(), true)
                || waypoint.rgb() < 0
                || waypoint.rgb() > 0xFFFFFF) {
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
                || utf8Length(value) > 1_024) {
            return false;
        }
        return value.chars().noneMatch(Character::isISOControl);
    }

    private static long retainedBytes(SimpleWaypoint waypoint) {
        if (waypoint == null) {
            return 1;
        }
        return 32L + utf8Length(waypoint.name()) + utf8Length(waypoint.initials());
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    @FunctionalInterface
    public interface PlayerMessageSender<P> {
        void send(P player, Component message);
    }

    private static final class PendingUpload {
        private final UploadRequestBuffer request;
        private final UploadScope scope;
        private final UploadConflictPolicy conflictPolicy;
        private final boolean deleteMissing;
        private final Instant expiresAt;
        private final Map<String, WaypointFilesManagerCore.DimensionRevision> dimensionRevisions;
        private final Map<UploadListKey, List<SimpleWaypoint>> uploadedWaypoints = new LinkedHashMap<>();
        private int waypointCount;
        private long retainedBytes;
        private volatile ScheduledFuture<?> expiryTask;

        private PendingUpload(
                UploadRequestBuffer request,
                UploadScope scope,
                UploadConflictPolicy conflictPolicy,
                boolean deleteMissing,
                Instant expiresAt,
                Map<String, WaypointFilesManagerCore.DimensionRevision> dimensionRevisions
        ) {
            this.request = request;
            this.scope = scope;
            this.conflictPolicy = conflictPolicy;
            this.deleteMissing = deleteMissing;
            this.expiresAt = expiresAt;
            this.dimensionRevisions = Map.copyOf(dimensionRevisions);
        }

        private void cancelExpiry() {
            ScheduledFuture<?> task = this.expiryTask;
            if (task != null) {
                task.cancel(false);
            }
        }
    }

    private record UploadListKey(String dimensionName, String listName) {
    }

    private record NavigationReplacement(NavigationTarget previous, NavigationTarget updated) {
    }

    private static final class DimensionMergeSummary {
        private int added;
        private int replaced;
        private int deleted;
        private int unchanged;
        private int conflicts;
        private int skipped;
        private final Set<String> changedLists = new LinkedHashSet<>();
        private final Set<String> removedLists = new HashSet<>();
        private final Map<String, WaypointList> listUpdates = new LinkedHashMap<>();
        private final List<NavigationReplacement> navigationReplacements = new ArrayList<>();
    }

    private static final class MergeSummary {
        private int added;
        private int replaced;
        private int deleted;
        private int unchanged;
        private int conflicts;
        private int skipped;
        private int staleDimensions;
        private boolean saveFailed;
        private final List<DimensionWaypointData> dimensionUpdates = new ArrayList<>();
        private final List<NavigationReplacement> navigationReplacements = new ArrayList<>();

        private void add(DimensionMergeSummary dimensionSummary) {
            this.added += dimensionSummary.added;
            this.replaced += dimensionSummary.replaced;
            this.deleted += dimensionSummary.deleted;
            this.unchanged += dimensionSummary.unchanged;
            this.conflicts += dimensionSummary.conflicts;
            this.skipped += dimensionSummary.skipped;
            this.navigationReplacements.addAll(dimensionSummary.navigationReplacements);
        }
    }
}
