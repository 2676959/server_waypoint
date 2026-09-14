package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.handoff.*;
import _959.server_waypoint.crossserver.pairing.CanonicalKey;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.*;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class LiveHandoffRuntimeTest {
    enum Scenario { SUCCESS, DESTINATION_PERMISSION_DENIED, DESTINATION_REJECTED, WRONG_SOURCE, TRANSFER_FAILED, PROXY_DENIED, DESTINATION_PERMISSION_REVOKED, DISCONNECT, CLAIM_BEFORE_READY, DELAYED_ROUTE, DELAYED_FAILED, DELAYED_DISCONNECT, DELAYED_WRONG_ROUTE }
    @ParameterizedTest @EnumSource(Scenario.class)
    void plaintext(Scenario scenario) throws Exception { run(TransportMode.PLAINTEXT, scenario); }
    @ParameterizedTest @EnumSource(Scenario.class)
    void noise(Scenario scenario) throws Exception { run(TransportMode.NOISE_KK, scenario); }
    void run(TransportMode mode, Scenario scenario) throws Exception {
        var a = new RemoteServerId("a"); var b = new RemoteServerId("b"); UUID player = UUID.randomUUID();
        var key = new RemoteWaypointKey(b, "world", "list", "target");
        boolean delayed = scenario.name().startsWith("DELAYED_");
        CompletableFuture<TransferResult> transferCompletion = new CompletableFuture<>();
        AtomicBoolean physicallyArrived = new AtomicBoolean();
        var route = new AtomicReference<>(scenario == Scenario.WRONG_SOURCE ? b : a);
        AtomicInteger switches = new AtomicInteger(), teleports = new AtomicInteger();
        CompletableFuture<Result> feedback = new CompletableFuture<>(), arrived = new CompletableFuture<>();
        byte[] ap = CanonicalKey.generatePrivate(), bp = CanonicalKey.generatePrivate(), cp = CanonicalKey.generatePrivate();
        boolean noise = mode == TransportMode.NOISE_KK;
        var tcp = new TcpLimits(4, 2000, 5000, 64, 4194304, 10000);
        var life = new LifecycleSettings(true, 100, 100, 500);
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ThreadLocal<Boolean> owns = ThreadLocal.withInitial(() -> false);
        java.util.function.Consumer<Runnable> schedule = task -> owner.execute(() -> { owns.set(true); try { task.run(); } finally { owns.set(false); } });
        var firstSession = new AtomicReference<BackendHandoffSession<UUID, UUID>>();
        var secondSession = new AtomicReference<BackendHandoffSession<UUID, UUID>>();
        CompletableFuture<Runnable> readiness = new CompletableFuture<>();
        SourceHandoffService.Platform<UUID> sourcePlatform = new SourceHandoffService.Platform<>() {
            public boolean ownsThread(UUID p) { return owns.get(); }
            public UUID playerId(UUID p) { assertTrue(owns.get()); return p; }
            public boolean isCurrentPlayer(UUID p, UUID id) { assertTrue(owns.get()); return p.equals(id); }
            public boolean canTeleport(UUID p) { assertTrue(owns.get()); return true; }
            public boolean execute(UUID p, Runnable task, Runnable retired) {
                if (scenario == Scenario.CLAIM_BEFORE_READY && !readiness.isDone()) readiness.complete(task);
                else schedule.accept(task);
                return true;
            }
        };
        DestinationPlatform<UUID> destinationPlatform = new DestinationPlatform<>() {
            public CompletionStage<Boolean> canPrepare(UUID id) { return CompletableFuture.completedFuture(scenario != Scenario.DESTINATION_PERMISSION_DENIED); }
            public boolean ownsThread(UUID p) { return owns.get(); }
            public UUID playerId(UUID p) { assertTrue(owns.get()); return p; }
            public boolean isCurrentPlayer(UUID p) { assertTrue(owns.get()); return physicallyArrived.get() || route.get().equals(b); }
            public boolean canTeleport(UUID p) { assertTrue(owns.get()); return scenario != Scenario.DESTINATION_PERMISSION_REVOKED; }
            public boolean execute(UUID p, Runnable task, Runnable retired) { schedule.accept(task); return true; }
            public CompletionStage<Boolean> teleport(UUID p, DestinationResolver.Target target) {
                assertTrue(owns.get()); assertEquals(new WaypointPos(50, 70, 60), target.position());
                teleports.incrementAndGet(); return CompletableFuture.completedFuture(true);
            }
        };
        AtomicReference<CoordinatorHandoffRuntime> runtimeRef = new AtomicReference<>();
        try (NoiseKeys ak = CanonicalKey.noiseKeys(ap); NoiseKeys bk = CanonicalKey.noiseKeys(bp); NoiseKeys ck = CanonicalKey.noiseKeys(cp)) {
            CoordinatorAgent coordinator = new CoordinatorAgent(() -> new TcpCoordinator(new TcpEndpoint("127.0.0.1", 0), mode, noise ? ck : null,
                    Map.of(a, noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(ap)) : new byte[0],
                            b, noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(bp)) : new byte[0]), tcp, ProtocolLimits.DEFAULT), tcp, life);
            CoordinatorHandoffRuntime runtime = new CoordinatorHandoffRuntime(id -> Optional.of(new ProxyPlayerSnapshot(player, Optional.of(route.get()))),
                    request -> scenario == Scenario.PROXY_DENIED ? Result.UNAUTHORIZED : Result.SUCCESS, (id, source, target) -> {
                assertEquals(a, route.get()); switches.incrementAndGet();
                if (scenario == Scenario.DISCONNECT) {
                    runtimeRef.get().playerDisconnected(id); return CompletableFuture.completedFuture(TransferResult.CANCELLED);
                }
                if (scenario == Scenario.TRANSFER_FAILED) return CompletableFuture.completedFuture(TransferResult.CONNECTION_FAILED);
                physicallyArrived.set(true);
                if (!delayed) route.set(b);
                secondSession.get().destination().arrive(player, player).whenComplete((result, failure) -> {
                    if (failure != null) arrived.completeExceptionally(failure); else arrived.complete(result.result());
                });
                return delayed ? transferCompletion : CompletableFuture.completedFuture(TransferResult.SUCCESS);
            }, coordinator::catalogs);
            runtimeRef.set(runtime);
            coordinator.setSessionFactory(runtime::attach);
            assertEquals(TransportResult.SUCCESS, coordinator.start().toCompletableFuture().get());
            var endpoint = new TcpEndpoint("127.0.0.1", coordinator.status().port());
            byte[] pin = noise ? CanonicalKey.rawPublic(CanonicalKey.publicFromPrivate(cp)) : null;
            var data = Map.of("world", Map.of("list", new RemoteListSnapshot("list", new RemoteRevision(0), Map.of("target",
                    new RemoteWaypointSnapshot("target", "T", new WaypointPos(1, 64, 2), 0, 0, false, List.of(), "")))));
            AtomicLong revision = new AtomicLong();
            BackendAgent first = new BackendAgent(endpoint, mode, a, Set.of(), noise ? ak : null, pin, tcp, ProtocolLimits.DEFAULT, life);
            BackendAgent second = new BackendAgent(endpoint, mode, b, Set.of(), noise ? bk : null, pin, tcp, ProtocolLimits.DEFAULT, life,
                    new CatalogPublisher(b, "b", () -> data, revision::incrementAndGet, ProtocolLimits.DEFAULT, 50));
            first.setSessionFactory(channel -> {
                var session = new BackendHandoffSession<>(a, channel, sourcePlatform, destinationPlatform,
                        target -> DestinationResolver.Resolution.denied(Result.NOT_FOUND)); firstSession.set(session); return session;
            });
            second.setSessionFactory(channel -> {
                var session = new BackendHandoffSession<>(b, channel, sourcePlatform, destinationPlatform, target -> scenario == Scenario.DESTINATION_REJECTED
                        ? DestinationResolver.Resolution.denied(Result.NOT_FOUND)
                        : new DestinationResolver.Resolution(Result.SUCCESS, new DestinationResolver.Target(target, new WaypointPos(50, 70, 60), 0)));
                secondSession.set(session); return session;
            });
            try {
                first.start().toCompletableFuture().get(); second.start().toCompletableFuture().get();
                await(() -> firstSession.get() != null && secondSession.get() != null && first.remoteCatalogs().get(b) != null
                        && first.remoteCatalogs().get(b).state() == RemoteCatalogState.AVAILABLE);
                var snapshot = first.remoteCatalogs().get(b).snapshot();
                schedule.accept(() -> firstSession.get().source().initiate(player, new RemoteTeleportInitiator.Selection(key,
                        snapshot.catalogRevision(), snapshot.dimensions().get("world").get("list").listRevision()), feedback::complete));
                if (scenario == Scenario.CLAIM_BEFORE_READY) {
                    Runnable ready = readiness.get(5, TimeUnit.SECONDS);
                    route.set(b);
                    assertEquals(Result.UNAUTHORIZED, secondSession.get().destination().arrive(player, player)
                            .toCompletableFuture().get(5, TimeUnit.SECONDS).result());
                    assertEquals(0, switches.get()); assertEquals(0, teleports.get());
                    schedule.accept(ready);
                }
                if (delayed) {
                    await(() -> runtime.pendingClaimCount() == 1);
                    assertFalse(arrived.isDone());
                    assertEquals(0, teleports.get());
                    if (scenario == Scenario.DELAYED_DISCONNECT) runtime.playerDisconnected(player);
                    if (scenario != Scenario.DELAYED_WRONG_ROUTE) route.set(b);
                    transferCompletion.complete(scenario == Scenario.DELAYED_FAILED
                            ? TransferResult.CONNECTION_FAILED : TransferResult.SUCCESS);
                    if (scenario == Scenario.DELAYED_WRONG_ROUTE) {
                        assertEquals(Result.UNAUTHORIZED, arrived.get(5, TimeUnit.SECONDS));
                        runtime.playerDisconnected(player);
                    }
                }
                Result expected = switch (scenario) {
                    case SUCCESS, DELAYED_ROUTE -> Result.SUCCESS;
                    case DESTINATION_REJECTED -> Result.NOT_FOUND;
                    case WRONG_SOURCE, CLAIM_BEFORE_READY -> Result.WRONG_SOURCE;
                    case TRANSFER_FAILED, DELAYED_FAILED -> Result.TRANSFER_FAILED;
                    case DESTINATION_PERMISSION_DENIED, PROXY_DENIED, DESTINATION_PERMISSION_REVOKED -> Result.UNAUTHORIZED;
                    case DISCONNECT, DELAYED_DISCONNECT, DELAYED_WRONG_ROUTE -> Result.UNAVAILABLE;
                };
                assertEquals(expected, feedback.get(8, TimeUnit.SECONDS));
                if (scenario == Scenario.SUCCESS || scenario == Scenario.DELAYED_ROUTE) {
                    assertEquals(Result.SUCCESS, arrived.get(5, TimeUnit.SECONDS));
                    assertEquals(1, switches.get()); assertEquals(1, teleports.get());
                    assertEquals(Result.NOT_FOUND, secondSession.get().destination().arrive(player, player).toCompletableFuture().get().result());
                } else { assertEquals(0, teleports.get()); assertEquals(Set.of(Scenario.TRANSFER_FAILED, Scenario.DESTINATION_PERMISSION_REVOKED, Scenario.DISCONNECT, Scenario.DELAYED_FAILED, Scenario.DELAYED_DISCONNECT, Scenario.DELAYED_WRONG_ROUTE).contains(scenario) ? 1 : 0, switches.get()); }
                assertEquals(0, firstSession.get().source().pendingCount());
                assertEquals(0, runtime.pendingTransferCount());
                assertEquals(0, runtime.pendingClaimCount());
            } finally {
                first.stop().toCompletableFuture().get(); second.stop().toCompletableFuture().get();
                runtime.close(); coordinator.stop().toCompletableFuture().get();
            }
        } finally { owner.shutdownNow(); assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    static void await(BooleanSupplier check) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!check.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(check.getAsBoolean());
    }
}
