package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import _959.server_waypoint.crossserver.transport.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns source/destination services and bounded request correlation on one admitted TCP connection. */
public final class BackendHandoffSession<S, P> implements OperationalSession {
    private record Pending(int phase, long deadline, CompletableFuture<ApplicationMessage> reply) { }
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final QueuedChannelSender sender;
    private final SourceHandoffService<S> source;
    private final DestinationHandoffService<P> destination;
    private boolean closed;
    public BackendHandoffSession(RemoteServerId localId, TcpChannel channel, SourceHandoffService.Platform<S> sourcePlatform,
                                 DestinationPlatform<P> destinationPlatform, DestinationResolver resolver) {
        sender = new QueuedChannelSender(channel);
        source = new SourceHandoffService<>(localId, sourcePlatform, new SourceHandoffService.Link() {
            public CompletionStage<ApplicationMessage> prepare(UUID id, PrepareHandoff request) { return request(id, request, 20); }
            public CompletionStage<Result> transfer(UUID id, HandoffBinding binding) {
                // The exact prepared binding doubles as source-owner readiness confirmation.
                return request(id, new HandoffPrepared(binding), 21).thenApply(reply -> {
                    if (reply instanceof CompleteHandoff complete && complete.handoffId().equals(binding.handoffId())
                            && complete.playerId().equals(binding.playerId()) && complete.destination().equals(binding.target().serverId())) return complete.result();
                    if (reply instanceof ApplicationMessage.Error error) return error.reason();
                    if (reply instanceof CancelHandoff cancel && cancel.handoffId().equals(binding.handoffId())) return cancel.reason();
                    return Result.INVALID_REQUEST;
                });
            }
            public void cancel(UUID id, CancelHandoff cancel) { sender.send(id, cancel); }
        });
        destination = new DestinationHandoffService<>(localId, resolver, destinationPlatform, new DestinationHandoffService.CoordinatorLink() {
            public CompletionStage<ApplicationMessage> claim(UUID id, ClaimHandoff claim) { return request(id, claim, 23); }
            public boolean send(UUID id, ApplicationMessage message) { return sender.send(id, message); }
        }, DestinationHandoffService.Limits.DEFAULT);
    }
    public SourceHandoffService<S> source() { return source; }
    public DestinationHandoffService<P> destination() { return destination; }
    private CompletionStage<ApplicationMessage> request(UUID id, ApplicationMessage message, int phase) {
        CompletableFuture<ApplicationMessage> future = new CompletableFuture<>();
        synchronized (this) {
            if (closed || pending.size() >= 64 || pending.containsKey(id)) return CompletableFuture.completedFuture(new ApplicationMessage.Error(Result.BUSY));
            pending.put(id, new Pending(phase, System.nanoTime() + 15_000_000_000L, future));
        }
        if (!sender.send(id, message)) {
            synchronized (this) { pending.remove(id); }
            future.complete(new ApplicationMessage.Error(Result.UNAVAILABLE));
        }
        return future.minimalCompletionStage();
    }
    @Override public boolean receive(ApplicationEnvelope envelope) {
        ApplicationMessage message = envelope.message();
        int type = ApplicationCodec.typeId(message);
        if (type < 20 || type > 26 && type != 30) return false;
        Pending waiting;
        synchronized (this) {
            if (closed) return true;
            waiting = pending.get(envelope.requestId());
            if (waiting != null && (type == 30 || type == 26 || waiting.phase() == 20 && (type == 21 || type == 22)
                    || waiting.phase() == 23 && type == 24 || waiting.phase() == 21 && type == 25)) pending.remove(envelope.requestId());
            else waiting = null;
        }
        if (waiting != null) { waiting.reply().complete(message); return true; }
        if (type == 30 && message instanceof ApplicationMessage.Error error && error.reason() == Result.STALE_CATALOG) return false;
        if (message instanceof PrepareHandoff prepare) sender.send(envelope.requestId(), destination.prepare(envelope.requestId(), prepare));
        else {
            source.receive(envelope.requestId(), message);
            destination.receive(envelope.requestId(), message);
        }
        return true;
    }
    @Override public void maintain() {
        List<Pending> expired = new ArrayList<>();
        synchronized (this) {
            long now = System.nanoTime();
            pending.values().removeIf(value -> { if (now - value.deadline() >= 0) { expired.add(value); return true; } return false; });
        }
        expired.forEach(value -> value.reply().complete(new ApplicationMessage.Error(Result.EXPIRED)));
        source.maintain(); destination.maintain();
    }
    @Override public void close() {
        List<Pending> remaining;
        synchronized (this) {
            if (closed) return;
            closed = true; remaining = List.copyOf(pending.values()); pending.clear();
        }
        sender.close(); source.close(); destination.close();
        remaining.forEach(value -> value.reply().complete(new ApplicationMessage.Error(Result.UNAVAILABLE)));
    }
}
