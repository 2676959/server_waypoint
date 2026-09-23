package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.transport.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Backend startup owns credentials, publication, replicas, and connection-scoped handoff services. */
public final class BackendRuntime<S, P> extends AsyncTransportLifecycle implements RemoteTeleportInitiator<S> {
    private final Path directory;
    private final WaypointServerCore manager;
    private final SourceHandoffService.Platform<S> sourcePlatform;
    private final DestinationPlatform<P> destinationPlatform;
    private CredentialFiles credentials;
    private CatalogRevisionSequence revisions;
    private volatile BackendAgent agent;
    private volatile BackendHandoffSession<S, P> session;
    public BackendRuntime(Path directory, WaypointServerCore manager, SourceHandoffService.Platform<S> sourcePlatform,
                          DestinationPlatform<P> destinationPlatform) {
        super("server-waypoint-backend-startup");
        this.directory = directory; this.manager = manager; this.sourcePlatform = sourcePlatform; this.destinationPlatform = destinationPlatform;
    }
    @Override protected TransportResult startResources() throws Exception {
        var config = RuntimeConfiguration.read(directory, Set.of("enabled", "transportMode", "serverId", "coordinator", "protocolVersion",
                "requiredSuite", "credentialsDirectory", "coordinatorPublicKey", "catalogExport", "serverIconItem"));
        if (!RuntimeConfiguration.enabled(config)) return TransportResult.DISABLED;
        RuntimeConfiguration.rejectCryptoInPlaintext(config);
        RemoteServerId id = new RemoteServerId(RuntimeConfiguration.text(config, "serverId", ""));
        if (!RuntimeConfiguration.text(config, "catalogExport", "PUBLIC").equals("PUBLIC")) throw new IllegalArgumentException("Only PUBLIC export is supported");
        var mode = RuntimeConfiguration.mode(config);
        NoiseKeys keys = null;
        byte[] pin = null;
        try {
            if (mode == TransportMode.NOISE_KK) {
                credentials = new CredentialFiles(directory.resolve(RuntimeConfiguration.text(config, "credentialsDirectory", "credentials")));
                LocalCredentials local = new LocalCredentials(credentials);
                Files.writeString(directory.resolve("cross-server-public-key.txt"), local.publicKey() + "\n");
                String configuredPin = RuntimeConfiguration.text(config, "coordinatorPublicKey", local.coordinatorPin());
                if (configuredPin == null || local.coordinatorPin() != null && !configuredPin.equals(local.coordinatorPin())) throw new IllegalArgumentException("Coordinator pin missing or changed");
                pin = CanonicalKey.rawPublic(configuredPin); keys = local.noiseKeys();
            }
            revisions = new CatalogRevisionSequence(directory.resolve("cross-server-catalog-state"));
            var selection = CatalogSelection.allPublic();
            var publisher = new CatalogPublisher(id, id.value(), CatalogSource.fromManager(manager, selection, 65536),
                    revisions::next, ProtocolLimits.DEFAULT, 1000, RuntimeConfiguration.text(config, "serverIconItem", ServerIcon.DEFAULT));
            agent = new BackendAgent(RuntimeConfiguration.endpoint(config, "coordinator"), mode, id, Set.of(), keys, pin,
                    TcpLimits.DEFAULT, ProtocolLimits.DEFAULT, new LifecycleSettings(true, 1000, 1000, 30000), publisher);
            agent.setSessionFactory(channel -> {
                var attached = new BackendHandoffSession<>(id, channel, sourcePlatform, destinationPlatform,
                        DestinationResolver.fromManager(id, manager, () -> selection, 65536));
                session = attached; return attached;
            });
            manager.setRemoteCatalogStore(agent.remoteCatalogStore());
            return agent.start().toCompletableFuture().get();
        } finally { if (keys != null) keys.close(); }
    }
    @Override public void initiate(S source, Selection selection, Consumer<Result> feedback) {
        var current = session;
        if (stopping || current == null) feedback.accept(Result.UNAVAILABLE);
        else current.source().initiate(source, selection, feedback);
    }
    public CompletionStage<DestinationHandoffService.ArrivalResult> arrive(UUID playerId, P player) {
        var current = session;
        if (stopping || current == null) return CompletableFuture.completedFuture(new DestinationHandoffService.ArrivalResult(Result.UNAVAILABLE, false));
        return current.destination().arrive(playerId, player);
    }
    @Override protected void stopResources() throws Exception {
        var current = session;
        if (current != null) current.close();
        if (agent != null) agent.stop().toCompletableFuture().get();
        session = null;
        manager.setRemoteCatalogStore(RemoteCatalogStore.empty());
        if (revisions != null) revisions.close();
        if (credentials != null) credentials.close();
    }
}
