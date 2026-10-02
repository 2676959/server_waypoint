package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One source service per coordinator connection. Close before reconnect; maintain periodically. */
public final class SourceHandoffService<S> implements RemoteTeleportInitiator<S>, AutoCloseable {
    public interface Platform<S> {
        boolean ownsThread(S source);
        UUID playerId(S source);
        boolean isCurrentPlayer(S source, UUID playerId);
        /** Recheck both local tp and remote tp permissions using the actual source player. */
        boolean canTeleport(S source);
        /** Queue once on the player owner; invoke retired on disappearance. Never block. */
        boolean execute(S source, Runnable task, Runnable retired);
    }
    public interface Link {
        /** Nonblocking, request-correlated reply from this exact admitted connection only. */
        CompletionStage<ApplicationMessage> prepare(UUID requestId, PrepareHandoff request);
        /** Ask the proxy to switch only after preparation. Recheck proxy UUID/source/expiry there. */
        CompletionStage<Result> transfer(UUID requestId, HandoffBinding binding);
        /** Best effort enqueue on this connection only. */
        void cancel(UUID requestId, CancelHandoff cancellation);
    }
    private enum State { PREPARING, READY, TRANSFERRING, TERMINAL }
    private final class Entry {
        final UUID requestId = UUID.randomUUID();
        final PrepareHandoff request;
        final S source;
        final Consumer<Result> feedback;
        final long bytes;
        long deadline;
        State state = State.PREPARING;
        HandoffBinding binding;
        Entry(S source, PrepareHandoff request, Consumer<Result> feedback, long bytes) {
            this.source = source; this.request = request; this.feedback = feedback; this.bytes = bytes;
            deadline = nanos.getAsLong() + 15_000_000_000L;
        }
    }
    private final RemoteServerId localId;
    private final Platform<S> platform;
    private final Link link;
    private final LongSupplier nanos, epoch;
    private final Map<UUID, Entry> players = new HashMap<>();
    private long retained;
    private boolean closed;

    public SourceHandoffService(RemoteServerId localId, Platform<S> platform, Link link) {
        this(localId, platform, link, System::nanoTime, System::currentTimeMillis);
    }
    public SourceHandoffService(RemoteServerId localId, Platform<S> platform, Link link,
                                LongSupplier nanos, LongSupplier epoch) {
        this.localId = Objects.requireNonNull(localId); this.platform = Objects.requireNonNull(platform);
        this.link = Objects.requireNonNull(link); this.nanos = Objects.requireNonNull(nanos); this.epoch = Objects.requireNonNull(epoch);
    }
    @Override public void initiate(S source, Selection selection, Consumer<Result> feedback) {
        Objects.requireNonNull(selection); Objects.requireNonNull(feedback);
        // Entry is called on the owner; never read a live player from a network completion.
        if (!platform.ownsThread(source)) throw new IllegalStateException("Source owner required");
        maintain();
        UUID playerId;
        try {
            playerId = platform.playerId(source);
            if (playerId == null || playerId.equals(new UUID(0, 0)) || !platform.isCurrentPlayer(source, playerId) || !platform.canTeleport(source)) {
                feedback.accept(Result.UNAUTHORIZED); return;
            }
        } catch (RuntimeException unavailable) { feedback.accept(Result.UNAVAILABLE); return; }
        if (localId.equals(selection.key().serverId())) { feedback.accept(Result.WRONG_DESTINATION); return; }
        Entry entry;
        synchronized (this) {
            if (closed) { feedback.accept(Result.UNAVAILABLE); return; }
            long bytes = 2048L + 2L * ((long) selection.key().dimensionName().length()
                    + selection.key().listName().length() + selection.key().waypointName().length());
            if (players.containsKey(playerId) || players.size() >= 64 || bytes > 1024 * 1024L - retained) {
                feedback.accept(Result.BUSY); return;
            }
            var request = new PrepareHandoff(playerId, localId, selection.key(), Action.TELEPORT,
                    selection.catalogRevision(), selection.listRevision());
            entry = new Entry(source, request, feedback, bytes);
            players.put(playerId, entry); retained += bytes;
            TeleportCoordinatorLog.activity(TeleportCoordinatorLog.BACKEND, "initiated", localId, entry.requestId, request);
            try {
                Objects.requireNonNull(link.prepare(entry.requestId, request)).whenComplete((reply, failure) -> prepared(entry, reply, failure));
            } catch (RuntimeException unavailable) { finish(entry, Result.UNAVAILABLE); }
        }
    }
    private void prepared(Entry entry, ApplicationMessage reply, Throwable failure) {
        Result denied = null;
        synchronized (this) {
            if (entry.state != State.PREPARING || closed) return;
            if (failure != null) denied = Result.UNAVAILABLE;
            else if (reply instanceof HandoffPrepared prepared && matches(entry.request, prepared.binding())) {
                entry.binding = prepared.binding();
                long remaining = entry.binding.expiresAtEpochMillis() - epoch.getAsLong();
                if (remaining <= 0 || expired(entry)) denied = Result.EXPIRED;
                else {
                    long now = nanos.getAsLong();
                    entry.deadline = now + Math.min(entry.deadline - now, Math.min(remaining, 15_000) * 1_000_000L);
                    entry.state = State.READY;
                }
            } else if (reply instanceof HandoffRejected rejected) denied = rejected.reason();
            else if (reply instanceof ApplicationMessage.Error error) denied = error.reason();
            else denied = Result.INVALID_REQUEST;
        }
        if (denied != null) finish(entry, denied);
        else schedule(entry, () -> transfer(entry));
    }
    private void transfer(Entry entry) {
        synchronized (this) { if (closed || entry.state != State.READY) return; }
        Result status = status(entry);
        synchronized (this) {
            if (closed || entry.state != State.READY) return;
            if (expired(entry)) status = Result.EXPIRED;
            if (status == Result.SUCCESS) {
                // Serialize the nonblocking transfer initiation with close/expiry.
                entry.state = State.TRANSFERRING;
                try {
                    Objects.requireNonNull(link.transfer(entry.requestId, entry.binding)).whenComplete((result, failure) ->
                            finish(entry, failure != null || result == null ? Result.TRANSFER_FAILED : result));
                } catch (RuntimeException unavailable) { finish(entry, Result.TRANSFER_FAILED); }
                return;
            }
        }
        finish(entry, status);
    }
    private Result status(Entry entry) {
        try {
            if (!platform.ownsThread(entry.source)) return Result.UNAVAILABLE;
            if (!platform.isCurrentPlayer(entry.source, entry.request.playerId())
                    || !entry.request.playerId().equals(platform.playerId(entry.source)) || !platform.canTeleport(entry.source)) return Result.UNAUTHORIZED;
            return Result.SUCCESS;
        } catch (RuntimeException unavailable) { return Result.UNAVAILABLE; }
    }
    private void schedule(Entry entry, Runnable task) {
        try {
            if (!platform.execute(entry.source, task, () -> finish(entry, Result.UNAVAILABLE))) finish(entry, Result.UNAVAILABLE);
        } catch (RuntimeException unavailable) { finish(entry, Result.UNAVAILABLE); }
    }
    private void finish(Entry entry, Result result) { finish(entry, result, true); }
    private void finish(Entry entry, Result result, boolean notify) {
        HandoffBinding binding;
        synchronized (this) {
            if (entry.state == State.TERMINAL) return;
            entry.state = State.TERMINAL; binding = entry.binding;
            players.remove(entry.request.playerId(), entry); retained -= entry.bytes;
        }
        TeleportCoordinatorLog.BACKEND.info("source_finished server={} request={} player={} result={}",
                TeleportCoordinatorLog.safe(localId.value()), entry.requestId, entry.request.playerId(), result);
        if (notify && binding != null && result != Result.SUCCESS) {
            try { link.cancel(entry.requestId, new CancelHandoff(binding.handoffId(), result)); }
            catch (RuntimeException unavailable) { /* Connection-scoped expiry is the fallback. */ }
        }
        try {
            platform.execute(entry.source, () -> {
                if (platform.ownsThread(entry.source) && platform.isCurrentPlayer(entry.source, entry.request.playerId())) {
                    entry.feedback.accept(result);
                }
            }, () -> { });
        } catch (RuntimeException unavailable) { /* Never send feedback to a replacement player. */ }
    }
    /** Consume cancellation/error on this exact coordinator connection before queued transfer work. */
    public boolean receive(UUID requestId, ApplicationMessage message) {
        Entry entry;
        Result reason;
        synchronized (this) {
            entry = players.values().stream().filter(value -> value.requestId.equals(requestId)).findFirst().orElse(null);
            if (closed || entry == null || entry.state == State.TRANSFERRING || entry.state == State.TERMINAL) return false;
            if (message instanceof CancelHandoff cancel && entry.binding != null
                    && entry.binding.handoffId().equals(cancel.handoffId())) reason = cancel.reason();
            else if (message instanceof ApplicationMessage.Error error) reason = error.reason();
            else if (message instanceof HandoffRejected rejected) reason = rejected.reason();
            else return false;
            // Keep cancellation atomic with transfer initiation.
            finish(entry, reason, false);
        }
        return true;
    }
    public void maintain() {
        List<Entry> expired;
        synchronized (this) { expired = players.values().stream().filter(this::expired).toList(); }
        expired.forEach(entry -> finish(entry, Result.EXPIRED));
    }
    public synchronized int pendingCount() { return players.size(); }
    @Override public void close() {
        List<Entry> pending;
        synchronized (this) {
            if (closed) return;
            closed = true; pending = List.copyOf(players.values());
        }
        pending.forEach(entry -> finish(entry, Result.UNAVAILABLE));
    }
    private boolean expired(Entry entry) { return nanos.getAsLong() - entry.deadline >= 0; }
    private static boolean matches(PrepareHandoff request, HandoffBinding binding) {
        return request.playerId().equals(binding.playerId()) && request.source().equals(binding.source())
                && request.target().equals(binding.target()) && request.action() == binding.action();
    }
}
