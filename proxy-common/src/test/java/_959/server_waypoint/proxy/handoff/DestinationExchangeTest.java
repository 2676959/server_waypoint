package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.handoff.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.proxy.ProxyPlayerSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Timeout(10)
class DestinationExchangeTest {
    @ParameterizedTest @EnumSource(TransportMode.class)
    void destinationServiceCompletesCoordinatorClaimUsingFreshTarget(TransportMode mode) {
        var a = new RemoteServerId("source"); var b = new RemoteServerId("destination");
        var source = new HandoffPeer(a, UUID.randomUUID(), mode); var destination = new HandoffPeer(b, UUID.randomUUID(), mode);
        UUID player = UUID.randomUUID();
        AtomicReference<UUID> request = new AtomicReference<>();
        var route = new AtomicReference<>(a);
        var key = new RemoteWaypointKey(b, "world", "Public", "Base");
        var position = new AtomicReference<>(new WaypointPos(1, 64, 2));
        var registry = new HandoffRegistry(HandoffLimits.DEFAULT);
        var coordinator = new HandoffRequestHandler(registry, id -> Optional.of(id.equals(a) ? source : destination),
                id -> Optional.of(new ProxyPlayerSnapshot(player, Optional.of(route.get()))), ignored -> Result.SUCCESS);
        AtomicBoolean owned = new AtomicBoolean(); AtomicInteger teleports = new AtomicInteger();
        DestinationPlatform<UUID> platform = new DestinationPlatform<>() {
            public CompletionStage<Boolean> canPrepare(UUID id) { return CompletableFuture.completedFuture(true); }
            public boolean execute(UUID p, Runnable task, Runnable retired) {
                boolean previous = owned.getAndSet(true);
                try { task.run(); } finally { owned.set(previous); } return true;
            }
            public boolean ownsThread(UUID p) { return owned.get(); }
            public UUID playerId(UUID p) { assertTrue(owned.get()); return p; }
            public boolean isCurrentPlayer(UUID p) { return p.equals(player); }
            public boolean canTeleport(UUID p) { assertTrue(owned.get()); return true; }
            public CompletionStage<Boolean> teleport(UUID p, DestinationResolver.Target target) {
                assertTrue(owned.get()); assertEquals(new WaypointPos(50, 64, 2), target.position());
                teleports.incrementAndGet(); return CompletableFuture.completedFuture(true);
            }
        };
        DestinationHandoffService.CoordinatorLink link = new DestinationHandoffService.CoordinatorLink() {
            public CompletionStage<ApplicationMessage> claim(UUID id, ClaimHandoff claim) {
                var response = coordinator.handle(destination, id, claim);
                assertEquals(Result.SUCCESS, response.result());
                return CompletableFuture.completedFuture(response.deliveries().get(0).message());
            }
            public boolean send(UUID id, ApplicationMessage message) {
                assertEquals(Result.SUCCESS, coordinator.handle(destination, id, message).result()); return true;
            }
        };
        try (var service = new DestinationHandoffService<>(b, target -> new DestinationResolver.Resolution(Result.SUCCESS,
                new DestinationResolver.Target(target, position.get(), 30)), platform, link, DestinationHandoffService.Limits.DEFAULT)) {
            AtomicInteger switches = new AtomicInteger();
            _959.server_waypoint.proxy.TransferAdapter<RemoteServerId> transfer = (id, expectedSource, target) -> {
                assertEquals(player, id); assertEquals(a, expectedSource); assertEquals(a, route.get());
                assertEquals(HandoffRegistry.State.PREPARED, registry.find(request.get()).orElseThrow().state());
                switches.incrementAndGet(); route.set(target); position.set(new WaypointPos(50, 64, 2));
                assertEquals(Result.SUCCESS, service.arrive(player, player).toCompletableFuture().join().result());
                return CompletableFuture.completedFuture(_959.server_waypoint.proxy.TransferResult.SUCCESS);
            };
            SourceHandoffService.Platform<UUID> sourcePlatform = new SourceHandoffService.Platform<>() {
                public boolean ownsThread(UUID p) { return true; }
                public UUID playerId(UUID p) { return p; }
                public boolean isCurrentPlayer(UUID p, UUID id) { return p.equals(id) && route.get().equals(a); }
                public boolean canTeleport(UUID p) { return true; }
                public boolean execute(UUID p, Runnable task, Runnable retired) { task.run(); return true; }
            };
            SourceHandoffService.Link sourceLink = new SourceHandoffService.Link() {
                public CompletionStage<ApplicationMessage> prepare(UUID id, PrepareHandoff preparation) {
                    request.set(id);
                    assertEquals(preparation, coordinator.handle(source, id, preparation).deliveries().get(0).message());
                    assertEquals(0, switches.get());
                    var prepared = service.prepare(id, preparation).toCompletableFuture().join();
                    var response = coordinator.handle(destination, id, prepared);
                    assertEquals(Result.SUCCESS, response.result()); assertEquals(0, switches.get());
                    return CompletableFuture.completedFuture(response.deliveries().get(0).message());
                }
                public CompletionStage<Result> transfer(UUID id, HandoffBinding binding) {
                    return transfer.transfer(binding.playerId(), binding.source(), binding.target().serverId())
                            .thenApply(result -> result == _959.server_waypoint.proxy.TransferResult.SUCCESS ? Result.SUCCESS : Result.TRANSFER_FAILED);
                }
                public void cancel(UUID id, CancelHandoff cancel) { coordinator.handle(source, id, cancel); }
            };
            try (var sourceService = new SourceHandoffService<>(a, sourcePlatform, sourceLink)) {
                sourceService.initiate(player, new RemoteTeleportInitiator.Selection(key, new RemoteRevision(1), new RemoteRevision(1)), ignored -> { });
                assertEquals(1, switches.get());
                assertEquals(0, sourceService.pendingCount());
            }
            assertEquals(HandoffRegistry.State.COMPLETED, registry.find(request.get()).orElseThrow().state());
            assertEquals(0, registry.stats().active()); assertEquals(1, teleports.get());
            assertEquals(Result.NOT_FOUND, service.arrive(player, player).toCompletableFuture().join().result());
        }
    }
}
