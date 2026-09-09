package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.proxy.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Serial policy owner for live admitted sessions. Socket writes run on bounded private workers. */
public final class CoordinatorHandoffRuntime implements AutoCloseable {
    private final Map<RemoteServerId, Session> sessions = new HashMap<>();
    private final HandoffRegistry registry = new HandoffRegistry(HandoffLimits.DEFAULT);
    private final HandoffRequestHandler handler;
    private final Function<UUID, Optional<ProxyPlayerSnapshot>> players;
    private final Function<PrepareHandoff, Result> permission;
    private final TransferAdapter<RemoteServerId> transfer;
    private final Supplier<Map<RemoteServerId, CatalogReceiver.View>> catalogs;
    private boolean closed;
    public CoordinatorHandoffRuntime(Function<UUID, Optional<ProxyPlayerSnapshot>> players,
                                     Function<PrepareHandoff, Result> permission,
                                     TransferAdapter<RemoteServerId> transfer,
                                     Supplier<Map<RemoteServerId, CatalogReceiver.View>> catalogs) {
        this.players = Objects.requireNonNull(players); this.permission = Objects.requireNonNull(permission);
        this.transfer = Objects.requireNonNull(transfer); this.catalogs = Objects.requireNonNull(catalogs);
        handler = new HandoffRequestHandler(registry, id -> Optional.ofNullable(sessions.get(id))
                .filter(session -> !session.channel.isClosed()).map(session -> session.peer), players, this::admit);
    }
    private Result admit(PrepareHandoff request) {
        Result allowed = permission.apply(request);
        if (allowed != Result.SUCCESS) return allowed;
        var view = catalogs.get().get(request.target().serverId());
        if (view == null || view.state() == RemoteCatalogState.UNAVAILABLE) return Result.UNAVAILABLE;
        if (view.state() == RemoteCatalogState.UNAUTHORIZED) return Result.UNAUTHORIZED;
        if (view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) return Result.STALE_CATALOG;
        var snapshot = view.snapshot();
        var list = snapshot.dimensions().getOrDefault(request.target().dimensionName(), Map.of()).get(request.target().listName());
        if (list == null || !list.waypoints().containsKey(request.target().waypointName())) return Result.NOT_FOUND;
        return snapshot.catalogRevision().equals(request.observedCatalogRevision()) && list.listRevision().equals(request.observedListRevision())
                ? Result.SUCCESS : Result.STALE_CATALOG;
    }
    public synchronized OperationalSession attach(TcpChannel channel) {
        if (closed || sessions.containsKey(channel.serverId())) throw new IllegalStateException("Session not admitted");
        Session session = new Session(channel); sessions.put(channel.serverId(), session); return session;
    }
    private final class Session implements OperationalSession {
        final TcpChannel channel;
        final HandoffPeer peer;
        final QueuedChannelSender sender;
        Session(TcpChannel channel) {
            this.channel = channel; peer = new HandoffPeer(channel.serverId(), UUID.randomUUID(), channel.mode());
            sender = new QueuedChannelSender(channel);
        }
        public boolean receive(ApplicationEnvelope envelope) {
            int type = ApplicationCodec.typeId(envelope.message());
            if (type < 20 || type > 26) return false;
            synchronized (CoordinatorHandoffRuntime.this) {
                if (closed || sessions.get(peer.serverId()) != this) return true;
                var record = registry.find(envelope.requestId()).orElse(null);
                if (envelope.message() instanceof HandoffPrepared ready && record != null && record.source().equals(peer)) {
                    beginTransfer(this, envelope.requestId(), ready.binding());
                } else if (envelope.message() instanceof ClaimHandoff && !registry.transferStarted(envelope.requestId())) {
                    sender.send(envelope.requestId(), new ApplicationMessage.Error(Result.UNAUTHORIZED));
                } else deliver(handler.handle(peer, envelope.requestId(), envelope.message()).deliveries());
            }
            return true;
        }
        public void maintain() {
            synchronized (CoordinatorHandoffRuntime.this) {
                if (closed) return;
                deliver(handler.maintain());
            }
        }
        public void close() {
            synchronized (CoordinatorHandoffRuntime.this) {
                if (sessions.remove(peer.serverId(), this)) deliver(handler.disconnect(peer));
                sender.close();
            }
        }
    }
    private void beginTransfer(Session session, UUID id, HandoffBinding binding) {
        var record = registry.find(id).orElse(null);
        Result denied = Result.INVALID_REQUEST;
        if (record != null && record.state() == HandoffRegistry.State.PREPARED && binding.equals(record.binding())
                && record.source().equals(session.peer)) {
            Session destination = sessions.get(binding.target().serverId());
            if (destination == null || destination.channel.isClosed() || !destination.peer.equals(record.destination())) denied = Result.UNAVAILABLE;
            else if (!players.apply(binding.playerId()).filter(player -> player.playerId().equals(binding.playerId()))
                    .flatMap(ProxyPlayerSnapshot::currentServer).filter(binding.source()::equals).isPresent()) denied = Result.WRONG_SOURCE;
            else denied = admit(record.request());
        }
        if (denied == Result.SUCCESS) denied = registry.beginTransfer(session.peer, id, binding);
        if (denied != Result.SUCCESS) { failTransfer(session, id, binding, denied); return; }
        try {
            // Adapter must recheck current proxy route immediately before the actual asynchronous switch.
            Objects.requireNonNull(transfer.transfer(binding.playerId(), binding.source(), binding.target().serverId()))
                    .whenComplete((result, failure) -> {
                        if (failure == null && result == TransferResult.SUCCESS) return; // Destination owns claim and completion.
                        synchronized (CoordinatorHandoffRuntime.this) {
                            if (!closed && sessions.get(session.peer.serverId()) == session) {
                                failTransfer(session, id, binding, result == TransferResult.PERMISSION_DENIED ? Result.UNAUTHORIZED : Result.TRANSFER_FAILED);
                            }
                        }
                    });
        } catch (RuntimeException failure) { failTransfer(session, id, binding, Result.TRANSFER_FAILED); }
    }
    private void failTransfer(Session session, UUID id, HandoffBinding binding, Result reason) {
        var record = registry.find(id).orElse(null);
        if (record != null && record.source().equals(session.peer) && binding.equals(record.binding())
                && record.state() == HandoffRegistry.State.PREPARED) {
            deliver(handler.handle(session.peer, id, new CancelHandoff(binding.handoffId(), reason)).deliveries());
        }
        session.sender.send(id, new ApplicationMessage.Error(reason));
    }
    /** Proxy disconnect invalidates active records for that authenticated UUID without trusting a backend event. */
    public synchronized void playerDisconnected(UUID playerId) {
        // Active records are bounded; the registry owns the player index and terminal retention.
        for (var record : registry.disconnectPlayer(playerId)) {
            ApplicationMessage message = record.binding() == null ? new HandoffRejected(Result.UNAVAILABLE)
                    : new CancelHandoff(record.binding().handoffId(), Result.UNAVAILABLE);
            deliver(List.of(new HandoffRequestHandler.Delivery(record.source(), record.requestId(), message),
                    new HandoffRequestHandler.Delivery(record.destination(), record.requestId(), message)));
        }
    }
    private void deliver(List<HandoffRequestHandler.Delivery> deliveries) {
        for (var delivery : deliveries) {
            Session session = sessions.get(delivery.peer().serverId());
            if (session != null && session.peer.equals(delivery.peer()) && !session.channel.isClosed()) session.sender.send(delivery.requestId(), delivery.message());
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        List.copyOf(sessions.values()).forEach(session -> { session.channel.close(); session.sender.close(); });
        sessions.clear(); handler.close();
    }
}
