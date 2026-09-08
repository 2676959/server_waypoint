package _959.server_waypoint.proxy.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import java.io.IOException;
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
    private final ListenerFactory factory;
    private final TcpLimits limits;
    private final LifecycleSettings settings;
    private final ConnectionMetrics metrics = new ConnectionMetrics();
    private final ConcurrentMap<RemoteServerId, Live> peers = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private ExecutorService readers;
    private ScheduledThreadPoolExecutor writers;
    private volatile TcpCoordinator listener;
    private volatile boolean running;

    /** Factory runs on the control worker, never on the caller; it may use PairingCoordinator.listen(). */
    public CoordinatorAgent(ListenerFactory factory, TcpLimits limits, LifecycleSettings settings) {
        super("server-waypoint-coordinator-control");
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
    @Override protected TransportResult startResources() throws Exception {
        if (!settings.enabled()) return TransportResult.DISABLED;
        listener = factory.open();
        if (!limits.equals(listener.limits())) throw new IllegalArgumentException("Listener limits differ from worker limits");
        writers = new ScheduledThreadPoolExecutor(Math.min(4, limits.connections()), daemonThreads("server-waypoint-coordinator-heartbeat"));
        writers.setRemoveOnCancelPolicy(true);
        readers = Executors.newFixedThreadPool(limits.connections(), daemonThreads("server-waypoint-coordinator-reader"));
        running = true;
        for (int n = 0; n < limits.connections(); n++) readers.execute(this::acceptLoop);
        return TransportResult.SUCCESS;
    }
    private void acceptLoop() {
        while (!stopping && running) {
            TcpChannel channel = null;
            Live live = null;
            ScheduledFuture<?> heartbeat = null;
            try {
                channel = listener.accept();
                ApplicationEnvelope envelope = channel.receive().envelope();
                if (!(envelope.message() instanceof ApplicationMessage.RegisterServer registration)
                        || !registration.serverId().equals(channel.serverId()) || registration.protocolVersion() != 1
                        || !registration.capabilities().equals(channel.capabilities())) {
                    throw new IOException("Registration does not match admitted transcript");
                }
                long next = generation.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
                if (stopping || next == Long.MAX_VALUE) throw new IOException("Coordinator stopped or exhausted");
                live = new Live(channel, new BackendPresence(channel.serverId(), channel.mode(), channel.capabilities(), next, Instant.now()));
                if (peers.putIfAbsent(channel.serverId(), live) != null) throw new IOException("Duplicate registered ID");
                channel.send(envelope.requestId(), new ApplicationMessage.RegisterResult(channel.serverId(), ApplicationMessage.Result.SUCCESS));
                live.registered = true;
                metrics.registered();
                TcpChannel session = channel;
                heartbeat = writers.scheduleWithFixedDelay(() -> sendHeartbeat(session), settings.heartbeatMillis(),
                        settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
                while (!stopping && running) {
                    ApplicationEnvelope nextEnvelope = channel.receive().envelope();
                    if (!(nextEnvelope.message() instanceof ApplicationMessage.Heartbeat)) throw new IOException("Unexpected presence message");
                    metrics.receivedHeartbeat();
                }
            } catch (Exception failure) {
                if (!stopping && running) metrics.failed();
                if (listener.isClosed()) running = false;
            } finally {
                if (heartbeat != null) heartbeat.cancel(false);
                if (channel != null) channel.close();
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
        IOException failure = null;
        try { if (listener != null) listener.close(); } catch (IOException exception) { failure = exception; }
        terminate(readers);
        terminate(writers);
        peers.clear();
        if (failure != null) throw failure;
    }
}
