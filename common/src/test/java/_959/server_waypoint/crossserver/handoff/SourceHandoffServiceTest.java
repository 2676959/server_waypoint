package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceHandoffServiceTest {
    final RemoteServerId a = new RemoteServerId("a"), b = new RemoteServerId("b");
    final UUID player = UUID.randomUUID();
    final RemoteTeleportInitiator.Selection selection = new RemoteTeleportInitiator.Selection(
            new RemoteWaypointKey(b, "dimension", "list", "waypoint"), new RemoteRevision(7), new RemoteRevision(3));
    final CompletableFuture<ApplicationMessage> reply = new CompletableFuture<>();
    final CompletableFuture<Result> switched = new CompletableFuture<>();
    final Queue<Runnable> owner = new ArrayDeque<>();
    final List<Result> feedback = new ArrayList<>();
    final List<CancelHandoff> cancellations = new ArrayList<>();
    boolean authorized = true, current = true, owns = true, scheduled = true;
    boolean throwPrepare, throwTransfer;
    long now, wall = 1000;
    int preparations, transfers;
    UUID requestId;
    PrepareHandoff request;
    final SourceHandoffService<String> service = new SourceHandoffService<>(a, new SourceHandoffService.Platform<>() {
        public boolean ownsThread(String source) { return owns; }
        public UUID playerId(String source) { assertTrue(owns); return player; }
        public boolean isCurrentPlayer(String source, UUID id) { assertTrue(owns); return current && player.equals(id); }
        public boolean canTeleport(String source) { assertTrue(owns); return authorized; }
        public boolean execute(String source, Runnable task, Runnable retired) {
            if (!scheduled) { retired.run(); return false; }
            owner.add(task); return true;
        }
    }, new SourceHandoffService.Link() {
        public CompletionStage<ApplicationMessage> prepare(UUID id, PrepareHandoff value) {
            preparations++; requestId = id; request = value;
            if (throwPrepare) throw new IllegalStateException();
            return reply;
        }
        public CompletionStage<Result> transfer(UUID id, HandoffBinding binding) {
            assertTrue(owns); assertEquals(requestId, id); assertEquals(selection.key(), binding.target());
            transfers++;
            if (throwTransfer) throw new IllegalStateException();
            return switched;
        }
        public void cancel(UUID id, CancelHandoff cancel) { assertEquals(requestId, id); cancellations.add(cancel); }
    }, () -> now, () -> wall);
    void initiate() { service.initiate("player", selection, feedback::add); }
    HandoffBinding binding() { return new HandoffBinding(UUID.randomUUID(), player, a, selection.key(), Action.TELEPORT, 16000); }
    void prepared(HandoffBinding binding) {
        owns = false; reply.complete(new HandoffPrepared(binding)); owns = true;
    }
    void drain() { while (!owner.isEmpty()) owner.remove().run(); }
    @AfterEach void cleanup() { service.close(); drain(); }

    @Test void waitsForPreparationAndOwnerRecheckThenTransfersOnce() {
        initiate(); assertEquals(1, preparations); assertEquals(0, transfers);
        assertEquals(new RemoteRevision(7), request.observedCatalogRevision());
        assertEquals(new RemoteRevision(3), request.observedListRevision());
        assertEquals(player, request.playerId()); assertEquals(a, request.source());
        prepared(binding()); assertEquals(0, transfers);
        drain(); assertEquals(1, transfers); assertTrue(feedback.isEmpty());
        switched.complete(Result.SUCCESS); drain();
        assertEquals(List.of(Result.SUCCESS), feedback); assertEquals(0, service.pendingCount());
        service.maintain(); assertEquals(1, transfers); assertTrue(cancellations.isEmpty());
    }
    @ParameterizedTest @EnumSource(value = Result.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
    void everyRejectedPreparationLeavesPlayerAtSource(Result reason) {
        initiate(); reply.complete(new HandoffRejected(reason)); drain();
        assertEquals(0, transfers); assertEquals(List.of(reason), feedback); assertEquals(0, service.pendingCount());
    }
    @Test void initialPermissionDenialAndBusyDoNotSendAnotherPrepare() {
        authorized = false; initiate(); assertEquals(List.of(Result.UNAUTHORIZED), feedback); assertEquals(0, preparations);
        authorized = true; feedback.clear(); initiate(); initiate();
        assertEquals(List.of(Result.BUSY), feedback); assertEquals(1, preparations);
    }
    @Test void revokedPermissionAfterPreparationCancelsWithoutSwitching() {
        initiate(); prepared(binding()); authorized = false; drain();
        assertEquals(0, transfers); assertEquals(List.of(Result.UNAUTHORIZED), feedback); assertEquals(1, cancellations.size());
    }
    @Test void replacedPlayerNeverTransfersOrReceivesOldFeedback() {
        initiate(); prepared(binding()); current = false; drain();
        assertEquals(0, transfers); assertTrue(feedback.isEmpty()); assertEquals(0, service.pendingCount());
    }
    @Test void timeoutAndLatePreparationCannotSwitch() {
        initiate(); now = 15_000_000_000L; service.maintain(); drain();
        assertEquals(List.of(Result.EXPIRED), feedback); prepared(binding()); drain(); assertEquals(0, transfers);
    }
    @Test void ownerQueueDelayAndWallRollbackCannotExtendDeadline() {
        initiate(); prepared(binding()); wall = 1; now = 15_000_000_000L; drain();
        assertEquals(0, transfers); assertEquals(List.of(Result.EXPIRED), feedback);
    }
    @Test void shorterDestinationDeadlineIsEnforcedOnOwner() {
        initiate(); prepared(new HandoffBinding(UUID.randomUUID(), player, a, selection.key(), Action.TELEPORT, 1001));
        now = 1_000_000; drain(); assertEquals(0, transfers); assertEquals(List.of(Result.EXPIRED), feedback);
    }
    @Test void mismatchedBindingCannotTransfer() {
        initiate(); prepared(new HandoffBinding(UUID.randomUUID(), UUID.randomUUID(), a, selection.key(), Action.TELEPORT, 16000));
        drain(); assertEquals(0, transfers); assertEquals(List.of(Result.INVALID_REQUEST), feedback);
    }
    @Test void disconnectBeforeScheduledTransferRejectsAndClearsBusySlot() {
        initiate(); prepared(binding()); service.close(); drain();
        assertEquals(0, transfers); assertEquals(List.of(Result.UNAVAILABLE), feedback); assertEquals(0, service.pendingCount());
        initiate(); assertEquals(1, preparations);
    }
    @Test void failedSchedulingDoesNotSwitchOrLeakSlot() {
        initiate(); scheduled = false; prepared(binding());
        assertEquals(0, transfers); assertEquals(0, service.pendingCount()); assertEquals(1, cancellations.size());
    }
    @Test void transportExceptionIsUnavailableAndReleasesSlot() {
        throwPrepare = true; initiate(); drain(); assertEquals(List.of(Result.UNAVAILABLE), feedback); assertEquals(0, service.pendingCount());
    }
    @Test void transferExceptionIsReportedAndReservationCancelled() {
        throwTransfer = true; initiate(); prepared(binding()); drain();
        assertEquals(List.of(Result.TRANSFER_FAILED), feedback); assertEquals(1, cancellations.size());
    }
    @Test void coordinatorCancellationBeforeOwnerWorkPreventsTransfer() {
        initiate(); var binding = binding(); prepared(binding);
        assertFalse(service.receive(UUID.randomUUID(), new CancelHandoff(binding.handoffId(), Result.CANCELLED)));
        assertFalse(service.receive(requestId, new CancelHandoff(UUID.randomUUID(), Result.CANCELLED)));
        assertTrue(service.receive(requestId, new CancelHandoff(binding.handoffId(), Result.CANCELLED)));
        drain(); assertEquals(0, transfers); assertEquals(List.of(Result.CANCELLED), feedback);
    }
    @Test void invalidReplyAndExpiredBindingNeverTransfer() {
        initiate(); reply.complete(new Heartbeat()); drain();
        assertEquals(0, transfers); assertEquals(List.of(Result.INVALID_REQUEST), feedback);
    }
    @Test void expiredPreparedBindingIsCancelled() {
        initiate(); prepared(new HandoffBinding(UUID.randomUUID(), player, a, selection.key(), Action.TELEPORT, wall));
        drain(); assertEquals(0, transfers); assertEquals(List.of(Result.EXPIRED), feedback); assertEquals(1, cancellations.size());
    }
    @Test void failedTransferFutureAndDuplicateCompletionGiveOneOutcome() {
        initiate(); prepared(binding()); drain(); switched.completeExceptionally(new IllegalStateException()); drain();
        assertEquals(List.of(Result.TRANSFER_FAILED), feedback); assertEquals(1, transfers);
        service.maintain(); service.close(); drain(); assertEquals(1, feedback.size());
    }
    @Test void unavailableFutureAndInvalidReplyCannotTransfer() {
        initiate(); reply.completeExceptionally(new IllegalStateException()); drain();
        assertEquals(0, transfers); assertEquals(List.of(Result.UNAVAILABLE), feedback);
    }
}
