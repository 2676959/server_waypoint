package _959.server_waypoint.velocity;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.handoff.CoordinatorHandoffRuntime;
import _959.server_waypoint.proxy.transport.CoordinatorAgent;
import com.google.gson.JsonObject;
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
    private volatile String startupFailureDetails;
    VelocityRuntime(ProxyServer proxy, Path directory) {
        super("server-waypoint-velocity-startup"); this.proxy = proxy; this.directory = directory;
    }
    @Override protected TransportResult startResources() throws Exception {
        var config = RuntimeConfiguration.readCoordinator(directory);
        if (!RuntimeConfiguration.enabled(config)) return TransportResult.DISABLED;
        try {
            RuntimeConfiguration.rejectCryptoInPlaintext(config);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
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
        JsonObject backends;
        try { backends = config.getAsJsonObject("backends"); }
        catch (ClassCastException failure) {
            throw new IllegalArgumentException("Invalid backends in cross-server.json; use an object keyed by backend serverId", failure);
        }
        if (backends == null || backends.size() > 256) {
            throw new IllegalArgumentException("Invalid backends in cross-server.json; provide an object with at most 256 backend entries");
        }
        for (var entry : backends.entrySet()) {
            JsonObject value;
            try { value = entry.getValue().getAsJsonObject(); }
            catch (IllegalStateException failure) {
                throw new IllegalArgumentException("Invalid backends." + entry.getKey() + " in cross-server.json; use an object with enabled, velocityServer, and publicKey", failure);
            }
            List<String> unknownFields = value.keySet().stream()
                    .filter(field -> !Set.of("enabled", "velocityServer", "publicKey").contains(field)).sorted().toList();
            if (!unknownFields.isEmpty()) {
                throw new IllegalArgumentException("Unknown entries in backends." + entry.getKey() + " in cross-server.json: "
                        + String.join(", ", unknownFields) + "; remove them or correct their names");
            }
            if (!RuntimeConfiguration.enabled(value)) continue;
            if (mode == TransportMode.PLAINTEXT && value.has("publicKey")) {
                String reason = "transportMode PLAINTEXT conflicts with publicKey in enabled backend '" + entry.getKey()
                        + "' in cross-server.json; remove that publicKey for plaintext, or set transportMode to NOISE_KK";
                throw new IllegalArgumentException(reason);
            }
            RemoteServerId id;
            try { id = new RemoteServerId(entry.getKey()); }
            catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Invalid backend serverId '" + entry.getKey()
                        + "' in cross-server.json; use 1-64 lowercase letters, digits, underscores, or hyphens, starting with a letter or digit", failure);
            }
            String name = RuntimeConfiguration.text(value, "velocityServer", "");
            if (proxy.getServer(name).isEmpty()) {
                throw new IllegalArgumentException("Invalid backends." + entry.getKey() + ".velocityServer in cross-server.json; use a server name configured in Velocity");
            }
            mappings.put(id, name);
            if (mode == TransportMode.PLAINTEXT) pins.put(id, new byte[0]);
            else {
                try { pins.put(id, CanonicalKey.rawPublic(RuntimeConfiguration.text(value, "publicKey", ""))); }
                catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException("Invalid backends." + entry.getKey()
                            + ".publicKey in cross-server.json; paste that backend's canonical X25519 public key", failure);
                }
            }
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
    @Override protected void onStartFailure(Exception failure) { startupFailureDetails = failure.getMessage(); }
    String startupFailureDetails() { return startupFailureDetails; }
    void disconnected(UUID id) { var current = handoffs; if (current != null) current.playerDisconnected(id); }
    @Override protected void stopResources() throws Exception {
        if (handoffs != null) handoffs.close();
        if (agent != null) agent.stop().toCompletableFuture().get();
        if (keys != null) keys.close();
        if (credentials != null) credentials.close();
    }
}
