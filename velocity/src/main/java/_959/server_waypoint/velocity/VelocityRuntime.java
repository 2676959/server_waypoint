package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.handoff.CoordinatorHandoffRuntime;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.*;
import java.util.*;

/** All disk/listener startup happens on the lifecycle worker, never a Velocity event thread. */
final class VelocityRuntime extends AsyncTransportLifecycle {
    private final ProxyServer proxy;
    private final Path directory;
    private CredentialFiles credentials;
    private NoiseKeys keys;
    private CoordinatorAgent agent;
    private volatile CoordinatorHandoffRuntime handoffs;
    VelocityRuntime(ProxyServer proxy, Path directory) {
        super("server-waypoint-velocity-startup"); this.proxy = proxy; this.directory = directory;
    }
    @Override protected TransportResult startResources() throws Exception {
        var config = RuntimeConfiguration.read(directory, Set.of("enabled", "transportMode", "listen", "protocolVersion",
                "requiredSuite", "credentialsDirectory", "backends", "proxyPermission"));
        if (!RuntimeConfiguration.enabled(config)) return TransportResult.DISABLED;
        RuntimeConfiguration.rejectCryptoInPlaintext(config);
        var mode = RuntimeConfiguration.mode(config);
        if (mode == TransportMode.NOISE_KK) {
            credentials = new CredentialFiles(directory.resolve(RuntimeConfiguration.text(config, "credentialsDirectory", "credentials")));
            var local = new LocalCredentials(credentials);
            Files.writeString(directory.resolve("cross-server-public-key.txt"), local.publicKey() + "\n");
            keys = local.noiseKeys();
        }
        Map<RemoteServerId, String> mappings = new HashMap<>();
        Map<RemoteServerId, byte[]> pins = new HashMap<>();
        var backends = config.getAsJsonObject("backends");
        if (backends == null || backends.size() > 256) throw new IllegalArgumentException("Backend registry required");
        for (var entry : backends.entrySet()) {
            var value = entry.getValue().getAsJsonObject();
            if (!Set.of("enabled", "velocityServer", "publicKey").containsAll(value.keySet())) throw new IllegalArgumentException("Unknown backend field");
            if (!RuntimeConfiguration.enabled(value)) continue;
            RemoteServerId id = new RemoteServerId(entry.getKey());
            String name = RuntimeConfiguration.text(value, "velocityServer", "");
            if (proxy.getServer(name).isEmpty()) throw new IllegalArgumentException("Unknown Velocity server mapping");
            if (mode == TransportMode.PLAINTEXT && value.has("publicKey")) throw new IllegalArgumentException("Plaintext cannot configure a public key");
            mappings.put(id, name);
            pins.put(id, mode == TransportMode.PLAINTEXT ? new byte[0] : CanonicalKey.rawPublic(RuntimeConfiguration.text(value, "publicKey", "")));
        }
        var endpoint = RuntimeConfiguration.endpoint(config, "listen");
        var router = new VelocityPlayerRouter(proxy, mappings, RuntimeConfiguration.text(config, "proxyPermission", ""));
        agent = new CoordinatorAgent(() -> new TcpCoordinator(endpoint, mode, keys, pins, TcpLimits.DEFAULT, ProtocolLimits.DEFAULT),
                TcpLimits.DEFAULT, new LifecycleSettings(true, 1000, 1000, 30000));
        handoffs = new CoordinatorHandoffRuntime(router::findPlayer, request -> {
            if (router.findServer(request.source()).isEmpty() || router.findServer(request.target().serverId()).isEmpty()) return Result.UNAVAILABLE;
            return router.allowed(request.playerId()) ? Result.SUCCESS : Result.UNAUTHORIZED;
        }, router, agent::catalogs);
        agent.setSessionFactory(handoffs::attach);
        return agent.start().toCompletableFuture().get();
    }
    void disconnected(UUID id) { var current = handoffs; if (current != null) current.playerDisconnected(id); }
    @Override protected void stopResources() throws Exception {
        if (handoffs != null) handoffs.close();
        if (agent != null) agent.stop().toCompletableFuture().get();
        if (keys != null) keys.close();
        if (credentials != null) credentials.close();
    }
}
