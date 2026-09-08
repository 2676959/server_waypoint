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

class DestinationExchangeTest {
    @ParameterizedTest @EnumSource(TransportMode.class)
    void destinationServiceCompletesCoordinatorClaimUsingFreshTarget(TransportMode mode) {
        var a = new RemoteServerId("source"); var b = new RemoteServerId("destination");
        var source = new HandoffPeer(a, UUID.randomUUID(), mode); var destination = new HandoffPeer(b, UUID.randomUUID(), mode);
        UUID player = UUID.randomUUID(), request = UUID.randomUUID();
        var route = new AtomicReference<>(a);
        var key = new RemoteWaypointKey(b, "world", "Public", "Base");
        var position = new AtomicReference<>(new WaypointPos(1, 64, 2));
        var registry = new HandoffRegistry(HandoffLimits.DEFAULT);
        var coordinator = new HandoffRequestHandler(registry, id -> Optional.of(id.equals(a) ? source : destination),
                id -> Optional.of(new ProxyPlayerSnapshot(player, Optional.of(route.get()))), ignored -> Result.SUCCESS);
        AtomicBoolean owned = new AtomicBoolean(); AtomicInteger teleports = new AtomicInteger();
        DestinationPlatform<UUID> platform = new DestinationPlatform<>() {
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
            var preparation = new PrepareHandoff(player, a, key, Action.TELEPORT, new RemoteRevision(1), new RemoteRevision(1));
            assertEquals(preparation, coordinator.handle(source, request, preparation).deliveries().get(0).message());
            var prepared = service.prepare(request, preparation);
            assertEquals(Result.SUCCESS, coordinator.handle(destination, request, prepared).result());
            route.set(b); position.set(new WaypointPos(50, 64, 2));
            assertEquals(Result.SUCCESS, service.arrive(player, player).toCompletableFuture().join().result());
            assertEquals(HandoffRegistry.State.COMPLETED, registry.find(request).orElseThrow().state());
            assertEquals(0, registry.stats().active()); assertEquals(1, teleports.get());
            assertEquals(Result.NOT_FOUND, service.arrive(player, player).toCompletableFuture().join().result());
        }
    }
}
