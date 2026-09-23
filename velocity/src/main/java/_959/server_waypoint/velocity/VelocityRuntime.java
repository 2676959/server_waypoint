package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.handoff.CoordinatorHandoffRuntime;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import java.nio.file.*;
import java.util.*;

/** All disk/listener startup happens on the lifecycle worker, never a Velocity event thread. */
final class VelocityRuntime extends AsyncTransportLifecycle {
    private final ProxyServer proxy;
    private final Path directory;
    private final Logger logger;
    private CredentialFiles credentials;
    private NoiseKeys keys;
    private CoordinatorAgent agent;
    private volatile CoordinatorHandoffRuntime handoffs;
    VelocityRuntime(ProxyServer proxy, Path directory, Logger logger) {
        super("server-waypoint-velocity-startup"); this.proxy = proxy; this.directory = directory; this.logger = logger;
    }
    @Override protected TransportResult startResources() throws Exception {
        var config = RuntimeConfiguration.readCoordinator(directory);
        if (!RuntimeConfiguration.enabled(config)) return TransportResult.DISABLED;
        try {
            RuntimeConfiguration.rejectCryptoInPlaintext(config);
        } catch (java.io.IOException | IllegalArgumentException failure) {
            logger.warn("Server Waypoint coordinator configuration rejected: {}", failure.getMessage());
            throw failure;
        }
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
            if (mode == TransportMode.PLAINTEXT && value.has("publicKey")) {
                String reason = "transportMode PLAINTEXT conflicts with publicKey in enabled backend '" + entry.getKey()
                        + "' in cross-server.json; remove that publicKey for plaintext, or set transportMode to NOISE_KK";
                logger.warn("Server Waypoint coordinator configuration rejected: {}", reason);
                throw new IllegalArgumentException(reason);
            }
            RemoteServerId id = new RemoteServerId(entry.getKey());
            String name = RuntimeConfiguration.text(value, "velocityServer", "");
            if (proxy.getServer(name).isEmpty()) throw new IllegalArgumentException("Unknown Velocity server mapping");
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
