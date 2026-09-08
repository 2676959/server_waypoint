package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.protocol.*;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Outbound registration, optional publication, and bounded remote catalog replication. */
public final class BackendAgent extends AsyncTransportLifecycle implements BackendTransport {
    public enum State { NEW, DISABLED, CONNECTING, REGISTERED, BACKOFF, STOPPED }
    public record Status(State state, TransportMode mode, BackendPresence presence, ConnectionMetrics.Snapshot metrics) { }
    private final TcpEndpoint endpoint;
    private final TransportMode mode;
    private final RemoteServerId id;
    private final Set<Integer> capabilities;
    private final NoiseKeys keys;
    private final byte[] pin;
    private final TcpLimits limits;
    private final ProtocolLimits protocol;
    private final LifecycleSettings settings;
    private final CatalogPublisher publisher;
    private final CatalogIndex remoteCatalogs;
    private final RemoteCatalogStore remoteCatalogStore;
    private ScheduledThreadPoolExecutor publications;
    private final ConnectionMetrics metrics = new ConnectionMetrics();
    private final Object retry = new Object();
    private ExecutorService reader;
    private ScheduledThreadPoolExecutor writer;
    private volatile Socket connecting;
    private volatile TcpChannel channel;
    private volatile BackendPresence presence;
    private volatile State state = State.NEW;
    private long generation;

    /** Key material is copied and owned by this agent; close it using stop(), including when never started. */
    public BackendAgent(TcpEndpoint endpoint, TransportMode mode, RemoteServerId id, Set<Integer> capabilities,
                        NoiseKeys keys, byte[] coordinatorPin, TcpLimits limits, ProtocolLimits protocol,
                        LifecycleSettings settings) {
        this(endpoint, mode, id, capabilities, keys, coordinatorPin, limits, protocol, settings, null);
    }

    public BackendAgent(TcpEndpoint endpoint, TransportMode mode, RemoteServerId id, Set<Integer> capabilities,
                        NoiseKeys keys, byte[] coordinatorPin, TcpLimits limits, ProtocolLimits protocol,
                        LifecycleSettings settings, CatalogPublisher publisher) {
        this(endpoint, mode, id, capabilities, keys, coordinatorPin, limits, protocol, settings, publisher, CatalogCacheLimits.DEFAULT);
    }
    public BackendAgent(TcpEndpoint endpoint, TransportMode mode, RemoteServerId id, Set<Integer> capabilities,
                        NoiseKeys keys, byte[] coordinatorPin, TcpLimits limits, ProtocolLimits protocol,
                        LifecycleSettings settings, CatalogPublisher publisher, CatalogCacheLimits cacheLimits) {
        super("server-waypoint-backend-control");
        remoteCatalogs = new CatalogIndex(cacheLimits);
        remoteCatalogStore = new RemoteCatalogStore(remoteCatalogs);
        if (publisher != null && !publisher.serverId().equals(id)) throw new IllegalArgumentException("Publisher identity mismatch");
        this.publisher = publisher;
        this.endpoint = Objects.requireNonNull(endpoint);
        this.mode = Objects.requireNonNull(mode);
        this.id = Objects.requireNonNull(id);
        this.capabilities = Set.copyOf(capabilities);
        this.limits = Objects.requireNonNull(limits);
        this.protocol = Objects.requireNonNull(protocol);
        this.settings = Objects.requireNonNull(settings);
        settings.validate(limits);
        if (capabilities.size() > 64 || capabilities.stream().anyMatch(value -> value <= 0)
                || mode == TransportMode.PLAINTEXT && (keys != null || coordinatorPin != null)
                || settings.enabled() && mode == TransportMode.NOISE_KK
                && (keys == null || coordinatorPin == null || coordinatorPin.length != 32)) throw new IllegalArgumentException("Invalid backend configuration");
        this.pin = !settings.enabled() || coordinatorPin == null ? null : coordinatorPin.clone();
        if (!settings.enabled() || keys == null) this.keys = null;
        else {
            byte[] secret = keys.copyPrivate();
            try { this.keys = new NoiseKeys(secret); } finally { Arrays.fill(secret, (byte) 0); }
        }
    }
    public Map<RemoteServerId, CatalogReceiver.View> remoteCatalogs() { return remoteCatalogs.views(); }
    public RemoteCatalogStore remoteCatalogStore() { return remoteCatalogStore; }
    @Override public RemoteServerId serverId() { return id; }
    public Status status() {
        TcpChannel current = channel;
        BackendPresence currentPresence = stopping || current == null || current.isClosed() ? null : presence;
        return new Status(stopping ? State.STOPPED : state, mode, currentPresence, metrics.snapshot());
    }
    @Override protected TransportResult startResources() throws Exception {
        if (!settings.enabled()) { state = State.DISABLED; return TransportResult.DISABLED; }
        endpoint.validate(mode);
        writer = new ScheduledThreadPoolExecutor(1, daemonThreads("server-waypoint-backend-heartbeat"));
        writer.setRemoveOnCancelPolicy(true);
        writer.scheduleWithFixedDelay(remoteCatalogs::maintain, settings.heartbeatMillis(), settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
        if (publisher != null) {
            publications = new ScheduledThreadPoolExecutor(1, daemonThreads("server-waypoint-backend-catalog"));
            publications.setRemoveOnCancelPolicy(true);
        }
        reader = Executors.newSingleThreadExecutor(daemonThreads("server-waypoint-backend-reader"));
        reader.execute(this::run);
        return TransportResult.SUCCESS;
    }
    private void run() {
        int failures = 0;
        while (!stopping) {
            boolean registered = false;
            boolean heartbeatReceived = false;
            long connectedAt = 0;
            ScheduledFuture<?> heartbeat = null;
            ScheduledFuture<?> publication = null;
            TcpChannel current = null;
            try {
                state = State.CONNECTING;
                metrics.attempted();
                current = TcpBackend.connect(endpoint, mode, id, capabilities, keys, pin, limits, protocol, socket -> {
                    connecting = socket;
                    if (stopping) TcpWire.close(socket);
                });
                channel = current;
                if (stopping) throw new IOException("Agent stopped");
                UUID request = UUID.randomUUID();
                current.send(request, new ApplicationMessage.RegisterServer(id, 1, capabilities));
                ApplicationEnvelope response = current.receive().envelope();
                if (!request.equals(response.requestId()) || !(response.message() instanceof ApplicationMessage.RegisterResult result)
                        || !id.equals(result.serverId()) || result.result() != ApplicationMessage.Result.SUCCESS) {
                    throw new IOException("Registration rejected");
                }
                if (stopping || generation == Long.MAX_VALUE) throw new IOException("Agent stopped or exhausted");
                for (RemoteServerId source : remoteCatalogs.serverIds()) remoteCatalogs.connected(source, current, null, protocol);
                connectedAt = System.nanoTime();
                presence = new BackendPresence(id, mode, capabilities, ++generation, Instant.now());
                registered = true;
                metrics.registered();
                state = State.REGISTERED;
                TcpChannel session = current;
                heartbeat = writer.scheduleWithFixedDelay(() -> sendHeartbeat(session), settings.heartbeatMillis(),
                        settings.heartbeatMillis(), TimeUnit.MILLISECONDS);
                if (publisher != null) {
                    publisher.requestFullSnapshot();
                    publication = publications.scheduleWithFixedDelay(() -> {
                        if (stopping || session.isClosed()) return;
                        try { publisher.publish(session); } catch (IOException failure) { session.close(); }
                    }, 0, publisher.intervalMillis(), TimeUnit.MILLISECONDS);
                }
                while (!stopping) {
                    TcpChannel.Received received = current.receive();
                    ApplicationEnvelope envelope = received.envelope();
                    if (publisher != null && envelope.message() instanceof ApplicationMessage.Error error
                            && error.reason() == ApplicationMessage.Result.STALE_CATALOG) {
                        publisher.requestFullSnapshot(); continue;
                    }
                    if (!(envelope.message() instanceof ApplicationMessage.Heartbeat)) {
                        ApplicationMessage message = envelope.message();
                        RemoteServerId source;
                        if (message instanceof ApplicationMessage.CatalogMetadata m) source = m.serverId();
                        else if (message instanceof ApplicationMessage.CatalogSnapshot m) source = m.serverId();
                        else if (message instanceof ApplicationMessage.CatalogDelta m) source = m.serverId();
                        else if (message instanceof ApplicationMessage.CatalogInvalidate m) source = m.serverId();
                        else throw new IOException("Unexpected coordinator message");
                        if (source.equals(id)) throw new IOException("Coordinator echoed local catalog");
                        // The coordinator vouches for routing. This link's mode does not prove the source link's mode.
                        if (!remoteCatalogs.contains(source)) {
                            remoteCatalogs.connected(source, current, null, protocol);
                        }
                        if (remoteCatalogs.receive(source, current, received)) {
                            current.send(envelope.requestId(), new ApplicationMessage.Error(ApplicationMessage.Result.STALE_CATALOG));
                        }
                        if (message instanceof ApplicationMessage.CatalogInvalidate m && m.state() == RemoteCatalogState.UNAVAILABLE) {
                            remoteCatalogs.expire(source);
                        }
                        continue;
                    }
                    metrics.receivedHeartbeat(); heartbeatReceived = true;
                }
            } catch (Exception failure) {
                if (!stopping) metrics.failed();
            } finally {
                if (heartbeat != null) heartbeat.cancel(false);
                if (publication != null) publication.cancel(false);
                if (current != null) { current.close(); remoteCatalogs.disconnected(current); }
                Socket socket = connecting;
                if (socket != null) TcpWire.close(socket);
                connecting = null; channel = null; presence = null;
                if (registered) metrics.disconnected();
            }
            if (stopping) break;
            if (heartbeatReceived && System.nanoTime() - connectedAt >= settings.reconnectMaxMillis() * 1_000_000L) failures = 0;
            failures = Math.min(31, failures + 1);
            long ceiling = settings.reconnectDelay(failures);
            long delay = ThreadLocalRandom.current().nextLong(settings.reconnectMinMillis(), ceiling + 1);
            state = State.BACKOFF;
            synchronized (retry) {
                if (!stopping) {
                    try { retry.wait(delay); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        state = State.STOPPED;
    }
    private void sendHeartbeat(TcpChannel session) {
        if (stopping || session.isClosed()) return;
        try { session.send(UUID.randomUUID(), new ApplicationMessage.Heartbeat()); metrics.sentHeartbeat(); }
        catch (IOException failure) { session.close(); }
    }
    @Override protected void stopResources() throws Exception {
        synchronized (retry) { retry.notifyAll(); }
        Socket socket = connecting;
        if (socket != null) TcpWire.close(socket);
        TcpChannel current = channel;
        if (current != null) current.close();
        terminate(reader);
        terminate(writer);
        terminate(publications);
        remoteCatalogs.clear();
        if (keys != null) keys.close();
        presence = null; state = State.STOPPED;
    }
}
