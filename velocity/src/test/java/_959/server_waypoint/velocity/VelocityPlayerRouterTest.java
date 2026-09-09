package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.proxy.TransferResult;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class VelocityPlayerRouterTest {
    final UUID id = UUID.randomUUID();
    final RemoteServerId a = new RemoteServerId("a"), b = new RemoteServerId("b");
    boolean online = true, permission = true;
    String route = "alpha";
    int connections;
    final CompletableFuture<ConnectionRequestBuilder.Result> result = new CompletableFuture<>();
    final RegisteredServer destination = fake(RegisteredServer.class, Map.of("getServerInfo", args -> new ServerInfo("beta", new InetSocketAddress("127.0.0.1", 25566))));
    final ServerConnection source = fake(ServerConnection.class, Map.of("getServerInfo", args -> new ServerInfo(route, new InetSocketAddress("127.0.0.1", 25565))));
    final Player player = fake(Player.class, Map.of("getUniqueId", args -> id, "isActive", args -> online,
            "hasPermission", args -> permission, "getCurrentServer", args -> Optional.of(source),
            "createConnectionRequest", args -> { assertSame(destination, args[0]); connections++;
                return fake(ConnectionRequestBuilder.class, Map.of("connect", ignored -> result)); }));
    final ProxyServer proxy = fake(ProxyServer.class, Map.of("getPlayer", args -> online && id.equals(args[0]) ? Optional.of(player) : Optional.empty(),
            "getServer", args -> Set.of("alpha", "beta").contains(args[0]) ? Optional.of(destination) : Optional.empty()));
    final VelocityPlayerRouter router = new VelocityPlayerRouter(proxy, Map.of(a, "alpha", b, "beta"), "server_waypoint.remote");
    @Test void currentProxyUuidAndSourceAreCheckedAndTransferIsAsynchronous() {
        assertEquals(a, router.findPlayer(id).orElseThrow().currentServer().orElseThrow());
        var pending = router.transfer(id, a, b).toCompletableFuture();
        assertEquals(1, connections); assertFalse(pending.isDone());
        result.complete(fake(ConnectionRequestBuilder.Result.class, Map.of("isSuccessful", args -> true)));
        assertEquals(TransferResult.SUCCESS, pending.join());
    }
    @Test void wrongSourceOfflineAndPermissionDenialNeverConnect() {
        route = "beta"; assertEquals(TransferResult.SOURCE_MISMATCH, router.transfer(id, a, b).toCompletableFuture().join());
        route = "alpha"; permission = false; assertEquals(TransferResult.PERMISSION_DENIED, router.transfer(id, a, b).toCompletableFuture().join());
        online = false; assertEquals(TransferResult.PLAYER_OFFLINE, router.transfer(id, a, b).toCompletableFuture().join());
        assertEquals(0, connections);
    }
    @Test void sourceChangeAfterEarlierSnapshotIsRejected() {
        assertEquals(a, router.findPlayer(id).orElseThrow().currentServer().orElseThrow()); route = "unmapped";
        assertEquals(TransferResult.SOURCE_MISMATCH, router.transfer(id, a, b).toCompletableFuture().join()); assertEquals(0, connections);
    }
    @Test void failedConnectionAndUnknownDestinationHaveStableFailures() {
        assertEquals(TransferResult.UNKNOWN_DESTINATION, router.transfer(id, a, new RemoteServerId("unknown")).toCompletableFuture().join());
        var pending = router.transfer(id, a, b).toCompletableFuture(); result.completeExceptionally(new IllegalStateException());
        assertEquals(TransferResult.CONNECTION_FAILED, pending.join());
    }
    @Test void mappingMustBeUniqueAndPermissionCanBeExplicitlyDisabled() {
        assertThrows(IllegalArgumentException.class, () -> new VelocityPlayerRouter(proxy, Map.of(a, "alpha", b, "alpha"), ""));
        permission = false; assertTrue(new VelocityPlayerRouter(proxy, Map.of(a, "alpha"), "").allowed(id));
        assertFalse(router.allowed(UUID.randomUUID()));
    }
    @Test void spoofedClientAndBackendPluginMessagesAreConsumedWithoutAnyTransfer(@TempDir Path directory) {
        var plugin = new ServerWaypointVelocity(proxy, fake(org.slf4j.Logger.class, Map.of()), directory);
        var client = new PluginMessageEvent(player, source, ServerWaypointVelocity.RESERVED, new byte[32]);
        plugin.onPluginMessage(client); assertFalse(client.getResult().isAllowed());
        var backend = new PluginMessageEvent(source, player, ServerWaypointVelocity.RESERVED, new byte[0]);
        plugin.onPluginMessage(backend); assertFalse(backend.getResult().isAllowed());
        var unrelated = new PluginMessageEvent(player, source, MinecraftChannelIdentifier.create("other", "data"), new byte[0]);
        plugin.onPluginMessage(unrelated); assertTrue(unrelated.getResult().isAllowed()); assertEquals(0, connections);
    }
    @SuppressWarnings("unchecked") static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            var handler = handlers.get(method.getName());
            if (handler != null) return handler.apply(args);
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }
}
