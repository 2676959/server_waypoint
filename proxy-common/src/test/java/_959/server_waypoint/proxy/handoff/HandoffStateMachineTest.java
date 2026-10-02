package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.proxy.ProxyPlayerSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class HandoffStateMachineTest {
    private static final RemoteServerId A = new RemoteServerId("source"), B = new RemoteServerId("destination");
    private static final RemoteWaypointKey TARGET = new RemoteWaypointKey(B, "world", "Public", "Base");
    private final AtomicLong nanos = new AtomicLong(), wall = new AtomicLong(1_000_000);
    private final UUID player = UUID.randomUUID(), requestId = UUID.randomUUID();
    private final Map<RemoteServerId, HandoffPeer> peers = new HashMap<>();
    private final Map<UUID, ProxyPlayerSnapshot> players = new HashMap<>();
    private final AtomicReference<Result> admission = new AtomicReference<>(SUCCESS);
    private HandoffRegistry registry;
    private HandoffRequestHandler handler;
    private HandoffPeer source, destination;
    private final ApplicationCodec codec = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private final AtomicLong sequence = new AtomicLong();

    private void setup(TransportMode mode) { setup(mode, HandoffLimits.DEFAULT); }
    private void setup(TransportMode mode, HandoffLimits limits) {
        source = new HandoffPeer(A, UUID.randomUUID(), mode);
        destination = new HandoffPeer(B, UUID.randomUUID(), mode);
        peers.put(A, source); peers.put(B, destination); at(player, A);
        registry = new HandoffRegistry(limits, nanos::get, wall::get);
        handler = new HandoffRequestHandler(registry, id -> Optional.ofNullable(peers.get(id)),
                id -> Optional.ofNullable(players.get(id)), ignored -> admission.get());
    }
    private void at(UUID id, RemoteServerId server) { players.put(id, new ProxyPlayerSnapshot(id, Optional.of(server))); }
    private PrepareHandoff prepare() { return prepare(player); }
    private PrepareHandoff prepare(UUID playerId) { return new PrepareHandoff(playerId, A, TARGET, Action.TELEPORT, new RemoteRevision(3), new RemoteRevision(2)); }
    private HandoffRequestHandler.Dispatch send(HandoffPeer peer, UUID request, ApplicationMessage message) {
        // Exercise the frozen canonical wire values without TCP or game scheduling.
        var envelope = codec.decode(codec.encode(new ApplicationEnvelope(sequence.getAndIncrement(), request, message)));
        var result = handler.handle(peer, envelope.requestId(), envelope.message());
        for (var delivery : result.deliveries()) {
            assertEquals(delivery.message(), codec.decode(codec.encode(new ApplicationEnvelope(sequence.getAndIncrement(),
                    delivery.requestId(), delivery.message()))).message());
        }
        return result;
    }
    private HandoffBinding reserve() {
        var forwarded = send(source, requestId, prepare());
        assertEquals(SUCCESS, forwarded.result());
        assertEquals(List.of(new HandoffRequestHandler.Delivery(destination, requestId, prepare())), forwarded.deliveries());
        HandoffBinding binding = binding(UUID.randomUUID(), player, TARGET, wall.get() + 15_000);
        var confirmed = send(destination, requestId, new HandoffPrepared(binding));
        assertEquals(SUCCESS, confirmed.result());
        assertEquals(List.of(new HandoffRequestHandler.Delivery(source, requestId, new HandoffPrepared(binding))), confirmed.deliveries());
        return binding;
    }
    private HandoffBinding binding(UUID id, UUID playerId, RemoteWaypointKey target, long expires) {
        return new HandoffBinding(id, playerId, A, target, Action.TELEPORT, expires);
    }
    private ClaimHandoff claim(HandoffBinding binding) { return new ClaimHandoff(binding.handoffId(), player, B); }
    private CompleteHandoff completion(HandoffBinding binding, Result result) { return new CompleteHandoff(binding.handoffId(), player, B, result); }

    @ParameterizedTest @EnumSource(TransportMode.class)
    void fullExchangeRequiresDestinationPreparationAndConsumesClaimOnce(TransportMode mode) {
        setup(mode);
        HandoffBinding binding = reserve();
        assertEquals(mode, registry.find(requestId).orElseThrow().source().mode());
        var earlyClaim = send(destination, requestId, claim(binding));
        assertEquals(UNAUTHORIZED, earlyClaim.result());
        assertEquals(new ApplicationMessage.Error(UNAUTHORIZED), earlyClaim.deliveries().get(0).message());
        at(player, B);
        var claimed = send(destination, requestId, claim(binding));
        assertEquals(SUCCESS, claimed.result());
        assertEquals(new HandoffClaimed(binding), claimed.deliveries().get(0).message());
        assertEquals(REPLAY, send(destination, requestId, claim(binding)).result());
        var completed = send(destination, requestId, completion(binding, SUCCESS));
        assertEquals(SUCCESS, completed.result());
        assertEquals(source, completed.deliveries().get(0).peer());
        assertEquals(0, registry.stats().active());
        assertEquals(HandoffRegistry.State.COMPLETED, registry.find(requestId).orElseThrow().state());
        assertTrue(send(destination, requestId, completion(binding, SUCCESS)).deliveries().isEmpty());
        assertEquals(REPLAY, send(destination, requestId, completion(binding, TRANSFER_FAILED)).result());
        assertEquals(REPLAY, send(destination, requestId, claim(binding)).result());
        at(player, A);
        assertEquals(REPLAY, send(source, requestId, prepare()).result());
        assertEquals(SUCCESS, send(source, UUID.randomUUID(), prepare()).result());
    }

    @Test void prepareAndClaimEnforceProxyUuidSourceAndAdmission() {
        setup(TransportMode.NOISE_KK);
        players.clear();
        assertEquals(WRONG_SOURCE, send(source, requestId, prepare()).result());
        players.put(player, new ProxyPlayerSnapshot(UUID.randomUUID(), Optional.of(A)));
        assertEquals(WRONG_SOURCE, send(source, requestId, prepare()).result());
        at(player, B);
        assertEquals(WRONG_SOURCE, send(source, requestId, prepare()).result());
        at(player, A); admission.set(UNAUTHORIZED);
        assertEquals(UNAUTHORIZED, send(source, requestId, prepare()).result());
        admission.set(SUCCESS); peers.remove(B);
        assertEquals(UNAVAILABLE, send(source, requestId, prepare()).result());
        peers.put(B, destination);
        assertEquals(WRONG_SOURCE, send(destination, requestId, prepare()).result());
        assertEquals(0, registry.stats().records());
        assertFalse(registry.audit().isEmpty());
    }

    @Test void onlyOneActiveRequestPerPlayerIncludingDuringPreparation() {
        setup(TransportMode.NOISE_KK);
        assertEquals(SUCCESS, send(source, requestId, prepare()).result());
        assertEquals(REPLAY, send(source, requestId, prepare()).result());
        assertEquals(BUSY, send(source, UUID.randomUUID(), prepare()).result());
        UUID another = UUID.randomUUID(); at(another, A);
        assertEquals(SUCCESS, send(source, UUID.randomUUID(), prepare(another)).result());
        assertEquals(2, registry.stats().active());
    }

    @Test void reserveRejectsSubstitutedBindingAndNeverExtendsExpiry() {
        setup(TransportMode.NOISE_KK);
        send(source, requestId, prepare());
        UUID handoff = UUID.randomUUID();
        assertEquals(WRONG_DESTINATION, send(source, requestId, new HandoffPrepared(binding(handoff, player, TARGET, wall.get() + 1000))).result());
        assertEquals(INVALID_REQUEST, send(destination, requestId, new HandoffPrepared(binding(handoff, UUID.randomUUID(), TARGET, wall.get() + 1000))).result());
        assertEquals(INVALID_REQUEST, send(destination, requestId, new HandoffPrepared(binding(handoff, player,
                new RemoteWaypointKey(B, "world", "public", "Base"), wall.get() + 1000))).result());
        HandoffBinding valid = binding(handoff, player, TARGET, wall.get() + 1000);
        assertEquals(SUCCESS, send(destination, requestId, new HandoffPrepared(valid)).result());
        assertEquals(REPLAY, send(destination, requestId, new HandoffPrepared(valid)).result());
        assertEquals(INVALID_REQUEST, send(destination, requestId, completion(valid, SUCCESS)).result());
        nanos.set(1_000_000_000L); at(player, B);
        assertEquals(EXPIRED, send(destination, requestId, claim(valid)).result());
    }

    @Test void delayedDestinationResponseCannotExtendCoordinatorDeadline() {
        setup(TransportMode.NOISE_KK); send(source, requestId, prepare());
        long originalExpiry = registry.find(requestId).orElseThrow().expiresAtEpochMillis();
        nanos.addAndGet(2_000_000_000L); wall.addAndGet(2000);
        HandoffBinding proposed = binding(UUID.randomUUID(), player, TARGET, wall.get() + 15_000);
        var result = send(destination, requestId, new HandoffPrepared(proposed));
        assertEquals(SUCCESS, result.result());
        HandoffBinding accepted = ((HandoffPrepared) result.deliveries().get(0).message()).binding();
        assertEquals(originalExpiry, accepted.expiresAtEpochMillis());
        assertEquals(proposed.handoffId(), accepted.handoffId());
        nanos.set(15_000_000_000L); at(player, B);
        assertEquals(EXPIRED, send(destination, requestId, claim(accepted)).result());
    }

    @Test void claimsAndCompletionBindEveryIdentityAndCurrentSession() {
        setup(TransportMode.NOISE_KK);
        HandoffBinding binding = reserve(); at(player, B);
        UUID other = UUID.randomUUID(); at(other, B);
        assertEquals(UNAUTHORIZED, send(destination, requestId, new ClaimHandoff(binding.handoffId(), other, B)).result());
        assertEquals(WRONG_DESTINATION, send(source, requestId, new ClaimHandoff(binding.handoffId(), player, B)).result());
        assertEquals(NOT_FOUND, send(destination, UUID.randomUUID(), claim(binding)).result());
        assertEquals(NOT_FOUND, send(destination, requestId, new ClaimHandoff(UUID.randomUUID(), player, B)).result());
        HandoffPeer replacement = new HandoffPeer(B, UUID.randomUUID(), TransportMode.NOISE_KK);
        peers.put(B, replacement);
        assertEquals(UNAUTHORIZED, send(destination, requestId, claim(binding)).result());
        assertEquals(WRONG_DESTINATION, send(replacement, requestId, claim(binding)).result());
        peers.put(B, destination);
        assertEquals(SUCCESS, send(destination, requestId, claim(binding)).result());
        assertEquals(UNAUTHORIZED, send(destination, requestId, new CompleteHandoff(binding.handoffId(), other, B, SUCCESS)).result());
        assertEquals(NOT_FOUND, send(destination, UUID.randomUUID(), completion(binding, SUCCESS)).result());
        assertEquals(WRONG_DESTINATION, send(source, requestId, completion(binding, SUCCESS)).result());
        assertEquals(1, registry.stats().active());
    }

    @Test void cancellationIsIdempotentAndDoesNotRaceClaimedDestinationOwnership() {
        setup(TransportMode.NOISE_KK);
        HandoffBinding binding = reserve();
        var cancel = new CancelHandoff(binding.handoffId(), CANCELLED);
        var cancelled = send(source, requestId, cancel);
        assertEquals(SUCCESS, cancelled.result()); assertEquals(destination, cancelled.deliveries().get(0).peer());
        assertTrue(send(source, requestId, cancel).deliveries().isEmpty());
        assertEquals(REPLAY, send(source, requestId, new CancelHandoff(binding.handoffId(), TRANSFER_FAILED)).result());
        at(player, B); assertEquals(REPLAY, send(destination, requestId, claim(binding)).result());
        setup(TransportMode.NOISE_KK); binding = reserve(); at(player, B);
        assertEquals(SUCCESS, send(destination, requestId, claim(binding)).result());
        cancel = new CancelHandoff(binding.handoffId(), CANCELLED);
        assertEquals(WRONG_DESTINATION, send(source, requestId, cancel).result());
        assertEquals(SUCCESS, send(destination, requestId, cancel).result());
        assertEquals(REPLAY, send(destination, requestId, completion(binding, SUCCESS)).result());
    }

    @Test void destinationRejectionReleasesPlayerAndRejectsLateConfirmation() {
        setup(TransportMode.NOISE_KK);
        send(source, requestId, prepare());
        assertEquals(WRONG_DESTINATION, send(source, requestId, new HandoffRejected(NOT_FOUND)).result());
        var rejected = send(destination, requestId, new HandoffRejected(NOT_FOUND));
        assertEquals(source, rejected.deliveries().get(0).peer());
        assertEquals(new HandoffRejected(NOT_FOUND), rejected.deliveries().get(0).message());
        assertEquals(0, registry.stats().active());
        assertEquals(REPLAY, send(destination, requestId, new HandoffPrepared(binding(UUID.randomUUID(), player, TARGET, wall.get() + 1000))).result());
    }

    @Test void expiryUsesMonotonicTimeAcrossWallClockRollbackAndReportsBothPeers() {
        setup(TransportMode.NOISE_KK);
        HandoffBinding binding = reserve(); wall.set(1);
        nanos.set(15_000_000_000L);
        assertEquals(2, handler.maintain().size());
        assertTrue(handler.maintain().isEmpty());
        at(player, B); assertEquals(EXPIRED, send(destination, requestId, claim(binding)).result());
        assertEquals(EXPIRED, send(destination, requestId, completion(binding, SUCCESS)).result());
        nanos.addAndGet(60_000_000_000L);
        assertEquals(0, registry.stats().records()); assertEquals(0, registry.stats().retainedBytes());
        assertEquals(NOT_FOUND, send(destination, requestId, claim(binding)).result());
    }

    @Test void preparingAndClaimedStatesAlsoExpireWithoutMaintenance() {
        setup(TransportMode.PLAINTEXT);
        send(source, requestId, prepare()); nanos.set(15_000_000_000L);
        assertEquals(HandoffRegistry.State.EXPIRED, registry.find(requestId).orElseThrow().state());
        setup(TransportMode.PLAINTEXT);
        HandoffBinding binding = reserve(); at(player, B); send(destination, requestId, claim(binding));
        nanos.addAndGet(15_000_000_000L);
        assertEquals(EXPIRED, send(destination, requestId, completion(binding, SUCCESS)).result());
        assertEquals(0, registry.stats().active());
    }

    @Test void disconnectedOldSessionCannotCancelNewSessionWorkAndRestartLosesClaims() {
        setup(TransportMode.NOISE_KK);
        HandoffBinding binding = reserve();
        assertEquals(1, handler.disconnect(source).size());
        assertEquals(0, registry.stats().active());
        at(player, B); assertEquals(REPLAY, send(destination, requestId, claim(binding)).result());
        HandoffPeer old = source;
        source = new HandoffPeer(A, UUID.randomUUID(), TransportMode.NOISE_KK); peers.put(A, source); at(player, A);
        UUID fresh = UUID.randomUUID(); assertEquals(SUCCESS, send(source, fresh, prepare()).result());
        assertTrue(handler.disconnect(old).isEmpty()); assertEquals(1, registry.stats().active());
        handler.close(); assertEquals(0, registry.stats().records());
        assertEquals(UNAVAILABLE, send(source, UUID.randomUUID(), prepare()).result());
        setup(TransportMode.NOISE_KK); at(player, B);
        assertEquals(NOT_FOUND, send(destination, requestId, claim(binding)).result());
    }

    @Test void sourceRouteChangeBeforeConfirmationOrMissingSourceSessionDenies() {
        setup(TransportMode.NOISE_KK); send(source, requestId, prepare());
        at(player, B);
        var binding = binding(UUID.randomUUID(), player, TARGET, wall.get() + 1000);
        assertEquals(WRONG_SOURCE, send(destination, requestId, new HandoffPrepared(binding)).result());
        at(player, A); admission.set(UNAUTHORIZED);
        assertEquals(UNAUTHORIZED, send(destination, requestId, new HandoffPrepared(binding)).result());
        admission.set(SUCCESS); peers.remove(A);
        assertEquals(UNAVAILABLE, send(destination, requestId, new HandoffPrepared(binding)).result());
        peers.put(A, source); assertEquals(SUCCESS, send(destination, requestId, new HandoffPrepared(binding)).result());
        peers.remove(A); at(player, B);
        assertEquals(UNAVAILABLE, send(destination, requestId, claim(binding)).result());
    }

    @Test void registryClaimIsAtomicUnderConcurrentDestinationRequests() throws Exception {
        setup(TransportMode.NOISE_KK); HandoffBinding binding = reserve();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result>> results = new ArrayList<>();
            for (int i = 0; i < 32; i++) results.add(workers.submit(() -> {
                start.await(); return registry.claim(destination, requestId, claim(binding)).result();
            }));
            start.countDown(); int accepted = 0, replayed = 0;
            for (var result : results) {
                Result value = result.get(5, TimeUnit.SECONDS);
                if (value == SUCCESS) accepted++; else if (value == REPLAY) replayed++;
            }
            assertEquals(1, accepted); assertEquals(31, replayed);
        } finally { workers.shutdownNow(); }
    }

    @Test void concurrentPreparationsAndClaimCancellationRaceStayLinearizable() throws Exception {
        setup(TransportMode.NOISE_KK);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result>> requests = new ArrayList<>();
            for (int i = 0; i < 16; i++) requests.add(workers.submit(() -> {
                start.await(); return registry.prepare(UUID.randomUUID(), prepare(), source, destination).result();
            }));
            start.countDown(); int accepted = 0;
            for (var request : requests) if (request.get(5, TimeUnit.SECONDS) == SUCCESS) accepted++;
            assertEquals(1, accepted); assertEquals(1, registry.stats().active());
            setup(TransportMode.NOISE_KK); HandoffBinding binding = reserve();
            CountDownLatch race = new CountDownLatch(1);
            Future<Result> claim = workers.submit(() -> {
                race.await(); return registry.claim(destination, requestId, claim(binding)).result();
            });
            Future<Result> cancel = workers.submit(() -> {
                race.await(); return registry.cancel(source, requestId, new CancelHandoff(binding.handoffId(), CANCELLED)).result();
            });
            race.countDown(); Result claimed = claim.get(5, TimeUnit.SECONDS), cancelled = cancel.get(5, TimeUnit.SECONDS);
            assertTrue(claimed == SUCCESS && cancelled == WRONG_DESTINATION || claimed == REPLAY && cancelled == SUCCESS);
        } finally { workers.shutdownNow(); }
    }

    @Test void noClaimBeforePreparationAndReservedIdsCannotCollide() {
        setup(TransportMode.NOISE_KK);
        send(source, requestId, prepare());
        HandoffBinding binding = binding(UUID.randomUUID(), player, TARGET, wall.get() + 15_000);
        at(player, B);
        assertEquals(NOT_FOUND, send(destination, requestId, claim(binding)).result());
        at(player, A);
        assertEquals(SUCCESS, send(destination, requestId, new HandoffPrepared(binding)).result());
        UUID other = UUID.randomUUID(), otherRequest = UUID.randomUUID(); at(other, A);
        assertEquals(SUCCESS, send(source, otherRequest, prepare(other)).result());
        assertEquals(INVALID_REQUEST, send(destination, otherRequest, new HandoffPrepared(
                binding(binding.handoffId(), other, TARGET, wall.get() + 15_000))).result());
        assertEquals(HandoffRegistry.State.PREPARING, registry.find(otherRequest).orElseThrow().state());
        assertEquals(new RemoteRevision(3), registry.find(requestId).orElseThrow().request().observedCatalogRevision());
        assertEquals(new RemoteRevision(2), registry.find(requestId).orElseThrow().request().observedListRevision());
    }

    @Test void claimedFailureCompletionIsIdempotentAndUnrelatedCancellationCannotMutateState() {
        setup(TransportMode.NOISE_KK); HandoffBinding binding = reserve();
        HandoffPeer outsider = new HandoffPeer(new RemoteServerId("outsider"), UUID.randomUUID(), TransportMode.NOISE_KK);
        peers.put(outsider.serverId(), outsider);
        assertEquals(UNAUTHORIZED, send(outsider, requestId, new CancelHandoff(binding.handoffId(), CANCELLED)).result());
        assertEquals(NOT_FOUND, send(source, UUID.randomUUID(), new CancelHandoff(binding.handoffId(), CANCELLED)).result());
        at(player, B); send(destination, requestId, claim(binding));
        assertEquals(SUCCESS, send(destination, requestId, completion(binding, UNAUTHORIZED)).result());
        assertTrue(send(destination, requestId, completion(binding, UNAUTHORIZED)).deliveries().isEmpty());
        assertEquals(REPLAY, send(source, requestId, new CancelHandoff(binding.handoffId(), CANCELLED)).result());
        assertEquals(UNAUTHORIZED, registry.find(requestId).orElseThrow().result());
    }

    @Test void expiredDestinationConfirmationAndDestinationDisconnectFailClosed() {
        setup(TransportMode.NOISE_KK); send(source, requestId, prepare());
        assertEquals(EXPIRED, send(destination, requestId, new HandoffPrepared(
                binding(UUID.randomUUID(), player, TARGET, wall.get()))).result());
        assertEquals(0, registry.stats().active());
        setup(TransportMode.NOISE_KK); HandoffBinding binding = reserve(); at(player, B);
        send(destination, requestId, claim(binding));
        var notices = handler.disconnect(destination);
        assertEquals(1, notices.size()); assertEquals(source, notices.get(0).peer());
        assertEquals(REPLAY, send(destination, requestId, completion(binding, SUCCESS)).result());
        assertEquals(0, registry.stats().active());
    }

    @Test void capacityAndAuditRemainBoundedAndDoNotEvictLiveReplayRecords() {
        setup(TransportMode.NOISE_KK, new HandoffLimits(2, 2, 2, 10_000, 3, 1000, 2000));
        assertEquals(SUCCESS, send(source, requestId, prepare()).result());
        send(destination, requestId, new HandoffRejected(NOT_FOUND));
        UUID second = UUID.randomUUID(); assertEquals(SUCCESS, send(source, second, prepare()).result());
        send(destination, second, new HandoffRejected(NOT_FOUND));
        for (int i = 0; i < 20; i++) assertEquals(BUSY, send(source, UUID.randomUUID(), prepare()).result());
        assertEquals(REPLAY, send(source, requestId, prepare()).result());
        assertEquals(2, registry.stats().records()); assertEquals(3, registry.audit().size());
        String audit = registry.audit().toString();
        assertFalse(audit.contains(player.toString())); assertFalse(audit.contains(requestId.toString()));
        assertFalse(audit.contains("Base")); assertFalse(audit.contains("Public"));
        assertThrows(UnsupportedOperationException.class, () -> registry.audit().clear());
        nanos.addAndGet(2_000_000_000L); assertEquals(SUCCESS, send(source, UUID.randomUUID(), prepare()).result());
    }

    @Test void perSourceAndByteBudgetsRejectBeforeRetainingAnEntry() {
        setup(TransportMode.NOISE_KK, new HandoffLimits(4, 4, 1, 10_000, 4, 1000, 2000));
        send(source, requestId, prepare()); send(destination, requestId, new HandoffRejected(NOT_FOUND));
        assertEquals(BUSY, send(source, UUID.randomUUID(), prepare()).result());
        setup(TransportMode.NOISE_KK, new HandoffLimits(4, 4, 4, 2048, 4, 1000, 2000));
        assertEquals(BUSY, send(source, requestId, prepare()).result());
        assertEquals(0, registry.stats().retainedBytes());
    }

    @Test void callbackFailuresAreSafeAndUnexpectedMessagesDoNotCreateRecords() {
        setup(TransportMode.NOISE_KK);
        handler = new HandoffRequestHandler(registry, id -> { throw new IllegalStateException("secret-value"); },
                id -> Optional.empty(), ignored -> SUCCESS);
        var failed = send(source, requestId, prepare());
        assertEquals(UNAVAILABLE, failed.result()); assertFalse(failed.toString().contains("secret-value"));
        assertEquals(0, registry.stats().records());
        setup(TransportMode.NOISE_KK);
        assertEquals(UNSUPPORTED, send(source, requestId, new Heartbeat()).result());
        assertEquals(0, registry.stats().records());
        assertThrows(IllegalArgumentException.class, () -> new HandoffLimits(1, 2, 1, 2048, 1, 1000, 1000));
    }
}
