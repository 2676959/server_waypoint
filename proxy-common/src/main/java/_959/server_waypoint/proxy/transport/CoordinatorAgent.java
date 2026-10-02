package _959.server_waypoint.proxy.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.proxy.catalog.CatalogDistributor;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import java.io.IOException;
import _959.server_waypoint.crossserver.handoff.TeleportCoordinatorLog;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded coordinator presence owner. Registration never dispatches game/player operations. */
public final class CoordinatorAgent extends AsyncTransportLifecycle implements CoordinatorTransport {
    @FunctionalInterface public interface ListenerFactory { TcpCoordinator open() throws IOException; }
    private static final class Live {
        private final TcpChannel channel;
        private final BackendPresence presence;
        volatile boolean registered;
        Live(TcpChannel channel, BackendPresence presence) { this.channel = channel; this.presence = presence; }
        TcpChannel channel() { return channel; }
        BackendPresence presence() { return presence; }
    }
    public record Status(boolean running, int port, Map<RemoteServerId, BackendPresence> backends,
                         ConnectionMetrics.Snapshot metrics) {
        public Status { backends = Map.copyOf(backends); }
    }
    private java.util.function.Function<TcpChannel, OperationalSession> sessionFactory = channel -> null;
    /** Configure before start; close each operational owner before replacing its connection. */
    public void setSessionFactory(java.util.function.Function<TcpChannel, OperationalSession> factory) {
        sessionFactory = Objects.requireNonNull(factory);
    }
    private final ListenerFactory factory;
    private final TcpLimits limits;
    private final LifecycleSettings settings;
    private final ConnectionMetrics metrics = new ConnectionMetrics();
    private final ConcurrentMap<RemoteServerId, Live> peers = new ConcurrentHashMap<>();
    private final CatalogIndex catalogs;
    private final AtomicLong generation = new AtomicLong();
    private ExecutorService readers;
    private ScheduledThreadPoolExecutor writers;
    private ScheduledThreadPoolExecutor distributions;
    private volatile TcpCoordinator listener;
    private volatile boolean running;

    /** Factory runs on the control worker, never on the caller; it may use PairingCoordinator.listen(). */
    public CoordinatorAgent(ListenerFactory factory, TcpLimits limits, LifecycleSettings settings) {
        this(factory, limits, settings, CatalogCacheLimits.DEFAULT);
    }
    public CoordinatorAgent(ListenerFactory factory, TcpLimits limits, LifecycleSettings settings, CatalogCacheLimits cacheLimits) {
        super("server-waypoint-coordinator-control");
        catalogs = new CatalogIndex(cacheLimits);
        this.factory = Objects.requireNonNull(factory);
        this.limits = Objects.requireNonNull(limits);
        this.settings = Objects.requireNonNull(settings);
        settings.validate(limits);
    }
    public Status status() {
        Map<RemoteServerId, BackendPresence> snapshot = new HashMap<>();
        if (!stopping) peers.forEach((id, live) -> {
            if (live.registered && !live.channel().isClosed()) snapshot.put(id, live.presence());
        });
        TcpCoordinator current = listener;
        ConnectionMetrics.Snapshot counters = metrics.snapshot();
        return new Status(running && !stopping, current == null ? -1 : current.port(), snapshot,
                new ConnectionMetrics.Snapshot(current == null ? 0 : current.acceptedConnections(), counters.registrations(),
                        counters.disconnects(), counters.failures(), counters.sentHeartbeats(), counters.receivedHeartbeats()));
    }
    public Map<RemoteServerId, CatalogReceiver.View> catalogs() {
        return catalogs.views();
    }

    @Override protected TransportResult startResources() throws Exception {
        if (!settings.enabled()) return TransportResult.DISABLED;
        listener = factory.open();
        if (!limits.equals(listener.limits())) throw new IllegalArgumentException("Listener limits differ from worker limits");
        writers = new ScheduledThreadPoolExecutor(Math.min(4, limits.connections()), daemonThreads("server-waypoint-coordinator-heartbeat"));
        writers.setRemoveOnCancelPolicy(true);
        distributions = new ScheduledThreadPoolExecutor(Math.min(4, limits.connections()), daemonThreads("server-waypoint-coordinator-catalog"));
        distributions.setRemoveOnCancelPolicy(true);
        distributions.scheduleWithFixedDelay(catalogs::maintain, settings.heartbeatMillis(), settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
        readers = Executors.newFixedThreadPool(limits.connections(), daemonThreads("server-waypoint-coordinator-reader"));
        running = true;
        TeleportCoordinatorLog.PROXY.info("coordinator_listening port={}", listener.port());
        for (int n = 0; n < limits.connections(); n++) readers.execute(this::acceptLoop);
        return TransportResult.SUCCESS;
    }
    private void acceptLoop() {
        while (!stopping && running) {
            TcpChannel channel = null;
            Live live = null;
            OperationalSession operations = null;
            ScheduledFuture<?> maintenance = null;
            CatalogDistributor distributor = null;
            ScheduledFuture<?> distribution = null;
            ScheduledFuture<?> heartbeat = null;
            try {
                channel = listener.accept();
                ApplicationEnvelope envelope = channel.receive().envelope();
                if (!(envelope.message() instanceof ApplicationMessage.RegisterServer registration)
                        || !registration.serverId().equals(channel.serverId()) || registration.protocolVersion() != _959.server_waypoint.crossserver.CrossServerProtocol.PROTOCOL_VERSION
                        || !registration.capabilities().equals(channel.capabilities())) {
                    throw new IOException("Registration does not match admitted transcript");
                }
                long next = generation.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
                if (stopping || next == Long.MAX_VALUE) throw new IOException("Coordinator stopped or exhausted");
                live = new Live(channel, new BackendPresence(channel.serverId(), channel.mode(), channel.capabilities(), next, Instant.now()));
                if (peers.putIfAbsent(channel.serverId(), live) != null) throw new IOException("Duplicate registered ID");
                catalogs.connected(channel.serverId(), channel, channel.mode(), channel.protocolLimits());
                distributor = new CatalogDistributor(catalogs, channel);
                channel.send(envelope.requestId(), new ApplicationMessage.RegisterResult(channel.serverId(), ApplicationMessage.Result.SUCCESS));
                live.registered = true;
                metrics.registered();
                TeleportCoordinatorLog.PROXY.info("connection_registered server={} generation={} mode={}",
                        TeleportCoordinatorLog.safe(channel.serverId().value()), next, channel.mode());
                TcpChannel session = channel;
                operations = sessionFactory.apply(session);
                if (operations != null) {
                    OperationalSession owner = operations;
                    maintenance = writers.scheduleWithFixedDelay(owner::maintain, 100, 100, TimeUnit.MILLISECONDS);
                }
                heartbeat = writers.scheduleWithFixedDelay(() -> sendHeartbeat(session), settings.heartbeatMillis(),
                        settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
                CatalogDistributor updates = distributor;
                distribution = distributions.scheduleWithFixedDelay(() -> {
                    if (stopping || session.isClosed()) return;
                    try { updates.publish(); } catch (Exception failure) { session.close(); }
                }, 0, settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
                while (!stopping && running) {
                    TcpChannel.Received received = channel.receive();
                    ApplicationEnvelope nextEnvelope = received.envelope();
                    if (operations != null && operations.receive(nextEnvelope)) continue;
                    if (nextEnvelope.message() instanceof ApplicationMessage.Heartbeat) metrics.receivedHeartbeat();
                    else if (nextEnvelope.message() instanceof ApplicationMessage.Error error
                            && error.reason() == ApplicationMessage.Result.STALE_CATALOG) {
                        distributor.resynchronize(nextEnvelope.requestId());
                    } else if (catalogs.receive(channel.serverId(), channel, received)) {
                        channel.send(nextEnvelope.requestId(), new ApplicationMessage.Error(ApplicationMessage.Result.STALE_CATALOG));
                    }
                }
            } catch (Exception failure) {
                if (!stopping && running) {
                    metrics.failed();
                    TeleportCoordinatorLog.PROXY.warn("connection_failed server={} cause={}",
                            channel == null ? "unidentified" : TeleportCoordinatorLog.safe(channel.serverId().value()), failure.getClass().getSimpleName());
                }
                if (listener.isClosed()) running = false;
            } finally {
                if (maintenance != null) maintenance.cancel(false);
                if (operations != null) operations.close();
                if (heartbeat != null) heartbeat.cancel(false);
                if (distribution != null) distribution.cancel(false);
                if (channel != null) {
                    channel.close();
                    TeleportCoordinatorLog.PROXY.info("connection_closed server={} stopping={}", TeleportCoordinatorLog.safe(channel.serverId().value()), stopping);
                }
                if (channel != null) catalogs.disconnected(channel.serverId(), channel);
                if (live != null && peers.remove(live.presence().serverId(), live) && live.registered) metrics.disconnected();
            }
        }
    }
    private void sendHeartbeat(TcpChannel channel) {
        if (stopping || !running || channel.isClosed()) return;
        try { channel.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat()); metrics.sentHeartbeat(); }
        catch (IOException failure) { channel.close(); }
    }
    @Override public CompletionStage<TransportResult> disconnect(RemoteServerId id) {
        Objects.requireNonNull(id);
        return command(() -> {
            Live live = peers.get(id);
            TcpCoordinator current = listener;
            if (current != null) current.disconnect(id);
            if (live != null && peers.remove(id, live)) {
                live.channel().close(); if (live.registered) metrics.disconnected();
            }
            return TransportResult.SUCCESS;
        });
    }
    @Override protected void stopResources() throws Exception {
        running = false;
        TeleportCoordinatorLog.PROXY.info("coordinator_stopping");
        IOException failure = null;
        try { if (listener != null) listener.close(); } catch (IOException exception) { failure = exception; }
        terminate(readers);
        terminate(writers);
        terminate(distributions);
        peers.clear();
        catalogs.clear();
        if (failure != null) throw failure;
    }
}
