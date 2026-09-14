package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogSelection;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Timeout(10)
class DestinationHandoffServiceTest {
    @TempDir Path directory;
    private static final RemoteServerId SOURCE = new RemoteServerId("source"), DESTINATION = new RemoteServerId("destination");
    private static final RemoteWaypointKey KEY = new RemoteWaypointKey(DESTINATION, "world", "Public", "Base");
    private final UUID request = UUID.randomUUID(), playerId = UUID.randomUUID();
    private final AtomicLong nanos = new AtomicLong(), epoch = new AtomicLong(1_000_000);
    private final AtomicReference<CatalogSelection> selection = new AtomicReference<>(CatalogSelection.allPublic());
    private final Player player = new Player(playerId);
    private final Platform platform = new Platform();
    private final Link link = new Link();
    private WaypointFilesManagerCore manager;
    private DestinationHandoffService<Player> service;

    private static class Player {
        final UUID id;
        boolean current = true, allowed = true;
        Player(UUID id) { this.id = id; }
    }
    private final class Platform implements DestinationPlatform<Player> {
        final Deque<Runnable> work = new ArrayDeque<>(), retired = new ArrayDeque<>();
        final List<DestinationResolver.Target> teleports = new ArrayList<>();
        final CompletableFuture<Boolean> teleported = new CompletableFuture<>();
        CompletionStage<Boolean> preflight = CompletableFuture.completedFuture(true);
        public CompletionStage<Boolean> canPrepare(UUID id) { assertEquals(playerId, id); return preflight; }
        boolean owned, reject, lieAboutOwnership;
        int reads;
        public boolean execute(Player player, Runnable task, Runnable retirement) {
            if (reject) return false;
            work.add(task); retired.add(retirement); return true;
        }
        public boolean ownsThread(Player player) { return owned && !lieAboutOwnership; }
        private void assertOwner() { assertTrue(owned, "Player API called off owner"); reads++; }
        public UUID playerId(Player player) { assertOwner(); return player.id; }
        public boolean isCurrentPlayer(Player player) { assertOwner(); return player.current; }
        public boolean canTeleport(Player player) { assertOwner(); return player.allowed; }
        public CompletionStage<Boolean> teleport(Player player, DestinationResolver.Target target) {
            assertOwner(); teleports.add(target); return teleported;
        }
        void run() {
            Runnable task = work.removeFirst(); retired.removeFirst(); owned = true;
            try { task.run(); } finally { owned = false; }
        }
        void retire() { work.removeFirst(); retired.removeFirst().run(); }
    }
    private final class Link implements DestinationHandoffService.CoordinatorLink {
        final CompletableFuture<ApplicationMessage> reply = new CompletableFuture<>();
        final List<ApplicationMessage> sent = new ArrayList<>();
        int claims;
        ClaimHandoff claim;
        boolean queue = true;
        public CompletionStage<ApplicationMessage> claim(UUID id, ClaimHandoff claim) {
            assertEquals(request, id); this.claim = claim; claims++; return reply;
        }
        public boolean send(UUID id, ApplicationMessage message) {
            assertEquals(request, id); sent.add(message); return queue;
        }
    }
    @BeforeEach void setup() {
        manager = new WaypointFilesManagerCore(directory);
        add("Base", 1, 20);
        service = create(DestinationResolver.fromManager(DESTINATION, manager, selection::get, 100), DestinationHandoffService.Limits.DEFAULT);
    }
    private DestinationHandoffService<Player> create(DestinationResolver resolver, DestinationHandoffService.Limits limits) {
        return new DestinationHandoffService<>(DESTINATION, resolver, platform, link, limits, nanos::get, epoch::get);
    }
    private void add(String name, int x, int yaw) {
        manager.addWaypoint("world", "Public", new SimpleWaypoint(name, "B", new WaypointPos(x, 64, 5), 0xFF0000, yaw, false), ignored -> {});
    }
    private void remove() { manager.removeWaypoint("world", "Public", "Base", ignored -> {}); }
    private PrepareHandoff prepareMessage() {
        return new PrepareHandoff(playerId, SOURCE, KEY, Action.TELEPORT, new RemoteRevision(1), new RemoteRevision(1));
    }
    private HandoffBinding prepare() {
        var response = service.prepare(request, prepareMessage()).toCompletableFuture().join();
        assertInstanceOf(HandoffPrepared.class, response); return ((HandoffPrepared) response).binding();
    }
    private CompletableFuture<DestinationHandoffService.ArrivalResult> arrive() {
        return service.arrive(playerId, player).toCompletableFuture();
    }
    private void grant(HandoffBinding binding) { link.reply.complete(new HandoffClaimed(binding)); }
    private void assertFailure(CompletableFuture<DestinationHandoffService.ArrivalResult> result, Result expected) {
        assertEquals(expected, result.join().result()); assertTrue(platform.teleports.isEmpty());
    }

    @Test void deniedPermissionNeverPreparesOrClaims() {
        platform.preflight = CompletableFuture.completedFuture(false);
        assertEquals(new HandoffRejected(UNAUTHORIZED), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        assertEquals(NOT_FOUND, arrive().join().result());
        assertEquals(0, link.claims);
        assertTrue(platform.teleports.isEmpty());
    }

    @Test void pendingPermissionCannotAdmitArrivalAndLateGrantCannotReviveExpiredRequest() {
        CompletableFuture<Boolean> permission = new CompletableFuture<>();
        platform.preflight = permission;
        var preparation = service.prepare(request, prepareMessage()).toCompletableFuture();
        assertFalse(preparation.isDone());
        assertEquals(REPLAY, arrive().join().result());
        nanos.addAndGet(16_000_000_000L);
        service.maintain();
        assertEquals(new HandoffRejected(EXPIRED), preparation.join());
        permission.complete(true);
        assertEquals(NOT_FOUND, arrive().join().result());
        assertEquals(0, link.claims);
    }

    @Test void disconnectCompletesPendingPreparationAndLateGrantCannotReviveIt() {
        CompletableFuture<Boolean> permission = new CompletableFuture<>();
        platform.preflight = permission;
        var preparation = service.prepare(request, prepareMessage()).toCompletableFuture();
        service.close();
        assertEquals(new HandoffRejected(UNAVAILABLE), preparation.join());
        permission.complete(true);
        assertEquals(0, service.stats().active());
        assertEquals(0, link.claims);
    }

    @Test void permissionFailureFailsClosed() {
        platform.preflight = CompletableFuture.failedFuture(new IllegalStateException("unavailable"));
        assertEquals(new HandoffRejected(UNAVAILABLE), service.prepare(request, prepareMessage()).toCompletableFuture().join());
    }

    @Test void resolvesMovedWaypointAfterClaimAndReportsOnlyActualAsyncSuccess() {
        HandoffBinding binding = prepare();
        assertEquals(0, platform.reads);
        var arrival = arrive(); assertEquals(0, link.claims); assertFalse(arrival.isDone());
        assertEquals(REPLAY, arrive().join().result());
        platform.run(); assertEquals(1, link.claims); assertEquals(playerId, link.claim.playerId());
        grant(binding); assertTrue(platform.teleports.isEmpty());
        remove(); add("Base", 90, 130);
        platform.run(); assertEquals(1, platform.teleports.size());
        assertEquals(new WaypointPos(90, 64, 5), platform.teleports.get(0).position());
        assertEquals(130, platform.teleports.get(0).yaw()); assertFalse(arrival.isDone());
        assertEquals(REPLAY, arrive().join().result());
        platform.teleported.complete(true);
        assertEquals(new DestinationHandoffService.ArrivalResult(SUCCESS, true), arrival.join());
        assertEquals(new CompleteHandoff(binding.handoffId(), playerId, DESTINATION, SUCCESS), link.sent.get(0));
        assertEquals(NOT_FOUND, arrive().join().result());
        assertEquals(1, link.claims); assertEquals(1, platform.teleports.size());
    }
    @Test void removalBetweenPrepareAndArrivalDeniesWithoutCachedCoordinates() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding); remove(); platform.run();
        assertFailure(arrival, NOT_FOUND); assertInstanceOf(CompleteHandoff.class, link.sent.get(0));
    }
    @Test void renameDoesNotFollowDisplayOrNearbyIdentity() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding);
        remove(); add("Renamed", 1, 20); platform.run(); assertFailure(arrival, NOT_FOUND);
    }
    @Test void currentExportSelectionAndPermissionAreRecheckedOnOwner() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding);
        selection.set(new CatalogSelection(false, Map.of())); platform.run(); assertFailure(arrival, UNAUTHORIZED);
    }
    @Test void permissionRevokedAfterClaimBeforeScheduledWorkDenies() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding);
        player.allowed = false; platform.run(); assertFailure(arrival, UNAUTHORIZED);
    }
    @Test void disconnectedOrDifferentPlayerCannotUseReservation() {
        prepare(); var arrival = service.arrive(playerId, new Player(UUID.randomUUID())).toCompletableFuture();
        platform.run(); assertFailure(arrival, UNAUTHORIZED); assertEquals(0, link.claims);
    }
    @Test void playerReplacementAfterClaimDeniesOnSecondOwnerPass() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding);
        player.current = false; platform.run(); assertFailure(arrival, UNAUTHORIZED);
    }
    @Test void schedulerRejectionAndRetirementDoNotStrandArrival() {
        prepare(); platform.reject = true; var arrival = arrive(); assertFailure(arrival, UNAVAILABLE);
        assertEquals(0, link.claims); assertEquals(0, service.stats().active());
    }
    @Test void retiredSecondOwnerTaskReportsFailureToCoordinator() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding); platform.retire();
        assertFailure(arrival, UNAVAILABLE); assertInstanceOf(CompleteHandoff.class, link.sent.get(0));
    }
    @Test void wrongThreadNeverReadsPlayerOrTeleports() {
        prepare(); var arrival = arrive(); platform.lieAboutOwnership = true; platform.run();
        assertFailure(arrival, UNAVAILABLE); assertEquals(0, platform.reads);
    }
    @Test void cancelledScheduledArrivalCannotRunAfterwards() {
        HandoffBinding binding = prepare(); var arrival = arrive();
        assertTrue(service.receive(request, new CancelHandoff(binding.handoffId(), CANCELLED)));
        platform.run(); assertFailure(arrival, CANCELLED); assertEquals(0, link.claims);
    }
    @Test void cancellationWhileClaimIsPendingIgnoresLateGrant() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run();
        service.receive(request, new CancelHandoff(binding.handoffId(), CANCELLED)); grant(binding);
        assertFailure(arrival, CANCELLED); assertTrue(platform.work.isEmpty());
    }
    @Test void closeWhileClaimPendingIgnoresOldConnectionCallback() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); service.close(); grant(binding);
        assertFailure(arrival, UNAVAILABLE); assertTrue(platform.work.isEmpty()); assertTrue(link.sent.isEmpty());
        assertEquals(0, service.stats().records());
    }
    @Test void malformedClaimCannotSubstitutePlayerTargetOrExtendReservation() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run();
        grant(new HandoffBinding(binding.handoffId(), UUID.randomUUID(), SOURCE, KEY, Action.TELEPORT, binding.expiresAtEpochMillis()));
        assertFailure(arrival, INVALID_REQUEST); assertTrue(platform.work.isEmpty());
    }
    @Test void coordinatorExpiryCapIsEnforcedBeforeQueuedTeleport() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run();
        grant(new HandoffBinding(binding.handoffId(), playerId, SOURCE, KEY, Action.TELEPORT, epoch.get() + 10));
        nanos.addAndGet(10_000_000L); platform.run(); assertFailure(arrival, EXPIRED);
    }
    @Test void expiredPendingClaimCannotTeleportEvenWithWallClockRollback() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run();
        epoch.set(1); nanos.set(15_000_000_000L); service.maintain(); grant(binding);
        assertFailure(arrival, EXPIRED); assertTrue(platform.work.isEmpty());
    }
    @Test void coordinatorFailureAndAsyncTeleportFailureNeverRetry() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding); platform.run();
        platform.teleported.completeExceptionally(new IllegalStateException("private failure"));
        assertEquals(TRANSFER_FAILED, arrival.join().result()); assertEquals(1, platform.teleports.size());
        assertEquals(new CompleteHandoff(binding.handoffId(), playerId, DESTINATION, TRANSFER_FAILED), link.sent.get(0));
    }
    @Test void deniedClaimDoesNotReportCompletionForAnUnclaimedHandoff() {
        prepare(); var arrival = arrive(); platform.run(); link.reply.complete(new ApplicationMessage.Error(UNAUTHORIZED));
        assertFailure(arrival, UNAUTHORIZED); assertTrue(link.sent.isEmpty());
    }
    @Test void reportQueueFailureDoesNotRepeatSuccessfulTeleport() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding); platform.run();
        link.queue = false; platform.teleported.complete(true);
        assertEquals(new DestinationHandoffService.ArrivalResult(SUCCESS, false), arrival.join());
        assertEquals(NOT_FOUND, arrive().join().result()); assertEquals(1, platform.teleports.size());
    }
    @Test void preparationIsBoundedAndReplayProtectedWithoutReadingPlayers() {
        prepare(); assertEquals(new HandoffRejected(REPLAY), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        assertEquals(new HandoffRejected(BUSY), service.prepare(UUID.randomUUID(), prepareMessage()).toCompletableFuture().join());
        assertEquals(0, platform.reads);
        nanos.set(15_000_000_000L); service.maintain();
        assertEquals(new HandoffRejected(REPLAY), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        nanos.addAndGet(60_000_000_000L); service.maintain(); assertEquals(0, service.stats().records());
        assertEquals(0, service.stats().retainedBytes());
    }
    @Test void authoritativeSourceUnavailableAndUnexportedTargetsRejectPreparation() {
        selection.set(new CatalogSelection(false, Map.of()));
        assertEquals(new HandoffRejected(UNAUTHORIZED), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        service = create(DestinationResolver.fromManager(DESTINATION, new WaypointFilesManagerCore(), selection::get, 100),
                DestinationHandoffService.Limits.DEFAULT);
        selection.set(CatalogSelection.allPublic());
        assertEquals(new HandoffRejected(UNAVAILABLE), service.prepare(request, prepareMessage()).toCompletableFuture().join());
    }
    @Test void concurrentArrivalEventsClaimAndTeleportOnlyOnce() throws Exception {
        HandoffBinding binding = prepare();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CompletableFuture<DestinationHandoffService.ArrivalResult>>> calls = new ArrayList<>();
            for (int i = 0; i < 16; i++) calls.add(workers.submit(() -> { start.await(); return arrive(); }));
            start.countDown();
            List<CompletableFuture<DestinationHandoffService.ArrivalResult>> arrivals = new ArrayList<>();
            for (var call : calls) arrivals.add(call.get(5, TimeUnit.SECONDS));
            assertEquals(1, arrivals.stream().filter(future -> !future.isDone()).count());
            assertEquals(15, arrivals.stream().filter(CompletableFuture::isDone).filter(future -> future.join().result() == REPLAY).count());
            platform.run(); grant(binding); platform.run(); platform.teleported.complete(true);
            assertEquals(1, link.claims); assertEquals(1, platform.teleports.size());
        } finally { workers.shutdownNow(); }
    }
    @Test void closeAfterInitiationDoesNotRetryOrUseReplacementConnection() {
        HandoffBinding binding = prepare(); var arrival = arrive(); platform.run(); grant(binding); platform.run();
        service.close(); platform.teleported.complete(true);
        assertEquals(UNAVAILABLE, arrival.join().result()); assertEquals(1, platform.teleports.size());
        assertTrue(link.sent.isEmpty()); assertEquals(0, service.stats().active());
    }

    @Test void resourceBudgetsAndResolverIdentityMismatchFailClosed() {
        service = create(key -> new DestinationResolver.Resolution(SUCCESS, new DestinationResolver.Target(
                new RemoteWaypointKey(DESTINATION, "world", "Public", "Other"), new WaypointPos(1, 2, 3), 0)),
                DestinationHandoffService.Limits.DEFAULT);
        assertEquals(new HandoffRejected(INVALID_REQUEST), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        service = create(DestinationResolver.fromManager(DESTINATION, manager, selection::get, 100),
                new DestinationHandoffService.Limits(1, 2048, 1000, 1000));
        assertEquals(new HandoffRejected(BUSY), service.prepare(request, prepareMessage()).toCompletableFuture().join());
        assertEquals(0, service.stats().records());
    }
}
