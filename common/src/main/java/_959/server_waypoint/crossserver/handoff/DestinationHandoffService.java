package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** One destination service per admitted coordinator connection. Close it before replacing that connection. */
public final class DestinationHandoffService<P> implements AutoCloseable {
    public interface CoordinatorLink {
        /** Correlate the reply to this request on this exact connection; never migrate a future across reconnect. */
        CompletionStage<ApplicationMessage> claim(UUID requestId, ClaimHandoff claim);
        /** Nonblocking enqueue to this connection. False means no notification was queued. */
        boolean send(UUID requestId, ApplicationMessage message);
    }
    public record Limits(int records, long retainedBytes, long expiryMillis, long retentionMillis) {
        public static final Limits DEFAULT = new Limits(1024, 8 * 1024 * 1024L, 15_000, 60_000);
        public Limits {
            if (records < 1 || records > 4096 || retainedBytes < 2048 || retainedBytes > 64 * 1024 * 1024L
                    || expiryMillis < 1 || expiryMillis > 15_000 || retentionMillis < expiryMillis || retentionMillis > 600_000) {
                throw new IllegalArgumentException("Invalid destination limits");
            }
        }
    }
    public record ArrivalResult(Result result, boolean notificationQueued) { }
    public record Stats(int records, int active, long retainedBytes, boolean closed) { }
    private enum State { CHECKING, PREPARED, SCHEDULED, CLAIMING, READY, VERIFYING, TELEPORTING, TERMINAL }
    private final class Entry {
        final UUID requestId;
        final HandoffBinding binding;
        final long bytes;
        final CompletableFuture<ApplicationMessage> preparation = new CompletableFuture<>();
        final CompletableFuture<ArrivalResult> arrival = new CompletableFuture<>();
        long deadline, terminalAt;
        State state = State.CHECKING;
        Result result = Result.SUCCESS;
        boolean claimed;
        P player;
        Entry(UUID requestId, HandoffBinding binding, long bytes, long deadline) {
            this.requestId = requestId; this.binding = binding; this.bytes = bytes; this.deadline = deadline;
        }
    }
    private final Object lock = new Object();
    private final RemoteServerId localId;
    private final DestinationResolver resolver;
    private final DestinationPlatform<P> platform;
    private final CoordinatorLink link;
    private final Limits limits;
    private final LongSupplier nanos, epoch;
    private final Map<UUID, Entry> requests = new HashMap<>();
    private final Map<UUID, Entry> players = new HashMap<>();
    private long retained;
    private boolean closed;

    public DestinationHandoffService(RemoteServerId localId, DestinationResolver resolver,
                                     DestinationPlatform<P> platform, CoordinatorLink link, Limits limits) {
        this(localId, resolver, platform, link, limits, System::nanoTime, System::currentTimeMillis);
    }
    public DestinationHandoffService(RemoteServerId localId, DestinationResolver resolver,
                                     DestinationPlatform<P> platform, CoordinatorLink link, Limits limits,
                                     LongSupplier nanos, LongSupplier epoch) {
        this.localId = Objects.requireNonNull(localId); this.resolver = Objects.requireNonNull(resolver);
        this.platform = Objects.requireNonNull(platform); this.link = Objects.requireNonNull(link);
        this.limits = Objects.requireNonNull(limits); this.nanos = Objects.requireNonNull(nanos); this.epoch = Objects.requireNonNull(epoch);
    }

    /** Called only for coordinator-forwarded preparation. Resolution is thread-safe authoritative model work. */
    public CompletionStage<ApplicationMessage> prepare(UUID requestId, PrepareHandoff request) {
        requireId(requestId); Objects.requireNonNull(request); maintain();
        Entry entry;
        synchronized (lock) {
            if (closed) return CompletableFuture.completedFuture(new HandoffRejected(Result.UNAVAILABLE));
            if (!localId.equals(request.target().serverId())) return CompletableFuture.completedFuture(new HandoffRejected(Result.WRONG_DESTINATION));
            if (requests.containsKey(requestId)) return CompletableFuture.completedFuture(new HandoffRejected(Result.REPLAY));
            long bytes = 2048L + 2L * ((long) request.target().dimensionName().length()
                    + request.target().listName().length() + request.target().waypointName().length());
            if (players.containsKey(request.playerId()) || requests.size() >= limits.records() || bytes > limits.retainedBytes() - retained) {
                return CompletableFuture.completedFuture(new HandoffRejected(Result.BUSY));
            }
            long expiry;
            try { expiry = Math.addExact(epoch.getAsLong(), limits.expiryMillis()); }
            catch (ArithmeticException invalidClock) { return CompletableFuture.completedFuture(new HandoffRejected(Result.UNAVAILABLE)); }
            if (expiry <= 0) return CompletableFuture.completedFuture(new HandoffRejected(Result.UNAVAILABLE));
            var binding = new HandoffBinding(UUID.randomUUID(), request.playerId(), request.source(), request.target(), request.action(), expiry);
            entry = new Entry(requestId, binding, bytes, nanos.getAsLong() + limits.expiryMillis() * 1_000_000L);
            requests.put(requestId, entry); players.put(request.playerId(), entry); retained += bytes;
        }
        try {
            Objects.requireNonNull(platform.canPrepare(request.playerId())).whenComplete((allowed, failure) -> {
                Result result = failure != null ? Result.UNAVAILABLE
                        : Boolean.TRUE.equals(allowed) ? Result.SUCCESS : Result.UNAUTHORIZED;
                prepared(entry, result);
            });
        } catch (RuntimeException unavailable) {
            prepared(entry, Result.UNAVAILABLE);
        }
        return entry.preparation.minimalCompletionStage();
    }

    private void prepared(Entry entry, Result permission) {
        synchronized (lock) {
            if (entry.state != State.CHECKING || closed) return;
        }
        Result resolution = permission == Result.SUCCESS ? resolve(entry).result() : permission;
        synchronized (lock) {
            if (closed) resolution = Result.UNAVAILABLE;
            else if (entry.state == State.TERMINAL) resolution = entry.result;
            else if (expired(entry)) resolution = Result.EXPIRED;
            if (resolution == Result.SUCCESS) {
                entry.state = State.PREPARED;
                entry.preparation.complete(new HandoffPrepared(entry.binding));
                return;
            }
        }
        finish(entry, resolution, false);
    }

    /** Use the UUID from the authenticated local join event, never from a forwarded claim message. */
    public CompletionStage<ArrivalResult> arrive(UUID authenticatedPlayerId, P player) {
        requireId(authenticatedPlayerId); Objects.requireNonNull(player); maintain();
        Entry entry;
        synchronized (lock) {
            if (closed) return CompletableFuture.completedFuture(new ArrivalResult(Result.UNAVAILABLE, false));
            entry = players.get(authenticatedPlayerId);
            if (entry == null) return CompletableFuture.completedFuture(new ArrivalResult(Result.NOT_FOUND, false));
            if (entry.state != State.PREPARED) return CompletableFuture.completedFuture(new ArrivalResult(Result.REPLAY, false));
            entry.player = player; entry.state = State.SCHEDULED;
        }
        schedule(entry, () -> startClaim(entry));
        // Do not expose a caller-completable/cancellable copy of the internal future.
        return entry.arrival.minimalCompletionStage();
    }

    private void schedule(Entry entry, Runnable task) {
        P player;
        synchronized (lock) {
            if (closed || entry.state == State.TERMINAL) return;
            player = entry.player;
        }
        try {
            if (!platform.execute(player, task, () -> finish(entry, Result.UNAVAILABLE, true, false))) {
                finish(entry, Result.UNAVAILABLE, true, false);
            }
        } catch (RuntimeException rejected) { finish(entry, Result.UNAVAILABLE, true, false); }
    }
    private Result playerStatus(Entry entry) {
        try {
            if (!platform.ownsThread(entry.player)) return Result.UNAVAILABLE;
            return entry.binding.playerId().equals(platform.playerId(entry.player)) && platform.isCurrentPlayer(entry.player)
                    ? Result.SUCCESS : Result.UNAUTHORIZED;
        } catch (RuntimeException unavailable) { return Result.UNAVAILABLE; }
    }
    private void startClaim(Entry entry) {
        synchronized (lock) {
            if (entry.state != State.SCHEDULED || closed) return;
            entry.state = State.CLAIMING;
        }
        Result status = playerStatus(entry);
        synchronized (lock) { if (expired(entry)) status = Result.EXPIRED; }
        if (status != Result.SUCCESS) { finish(entry, status, true); return; }
        try {
            synchronized (lock) { if (entry.state != State.CLAIMING || closed) return; }
            Objects.requireNonNull(link.claim(entry.requestId, new ClaimHandoff(entry.binding.handoffId(),
                    entry.binding.playerId(), localId))).whenComplete((reply, failure) -> claimed(entry, reply, failure));
        } catch (RuntimeException unavailable) { finish(entry, Result.UNAVAILABLE, true); }
    }
    private void claimed(Entry entry, ApplicationMessage reply, Throwable failure) {
        Result rejected = null;
        synchronized (lock) {
            if (entry.state != State.CLAIMING || closed) return;
            if (failure != null) rejected = Result.UNAVAILABLE;
            else if (reply instanceof HandoffClaimed claimed && matches(entry.binding, claimed.binding())) {
                entry.claimed = true;
                long remaining = claimed.binding().expiresAtEpochMillis() - epoch.getAsLong();
                if (remaining <= 0 || expired(entry)) rejected = Result.EXPIRED;
                else {
                    long now = nanos.getAsLong();
                    entry.deadline = now + Math.min(entry.deadline - now, Math.min(remaining, limits.expiryMillis()) * 1_000_000L);
                    entry.state = State.READY;
                }
            } else if (reply instanceof ApplicationMessage.Error error) rejected = error.reason();
            else if (reply instanceof HandoffRejected rejection) rejected = rejection.reason();
            else rejected = Result.INVALID_REQUEST;
        }
        if (rejected != null) { finish(entry, rejected, !(reply instanceof ApplicationMessage.Error || reply instanceof HandoffRejected)); return; }
        schedule(entry, () -> teleport(entry));
    }
    private void teleport(Entry entry) {
        synchronized (lock) {
            if (entry.state != State.READY || closed) return;
            entry.state = State.VERIFYING;
        }
        Result status = playerStatus(entry);
        if (status != Result.SUCCESS) { finish(entry, status, true); return; }
        try {
            if (!platform.canTeleport(entry.player)) { finish(entry, Result.UNAUTHORIZED, true); return; }
        } catch (RuntimeException unavailable) { finish(entry, Result.UNAVAILABLE, true); return; }
        var resolution = resolve(entry);
        if (resolution.result() != Result.SUCCESS) { finish(entry, resolution.result(), true); return; }
        CompletionStage<Boolean> completion = null;
        Result failed = null;
        synchronized (lock) {
            if (entry.state != State.VERIFYING || closed) return;
            if (expired(entry)) failed = Result.EXPIRED;
            else {
                // Serialize initiation with cancellation/close. Adapter must only initiate, never block.
                entry.state = State.TELEPORTING;
                try { completion = Objects.requireNonNull(platform.teleport(entry.player, resolution.target())); }
                catch (RuntimeException unavailable) { failed = Result.TRANSFER_FAILED; }
            }
        }
        if (failed != null) finish(entry, failed, true);
        else completion.whenComplete((success, failure) -> finish(entry,
                failure == null && Boolean.TRUE.equals(success) ? Result.SUCCESS : Result.TRANSFER_FAILED, true));
    }
    private DestinationResolver.Resolution resolve(Entry entry) {
        try {
            var result = Objects.requireNonNull(resolver.resolve(entry.binding.target()));
            if (result.target() != null && !result.target().key().equals(entry.binding.target())) {
                return DestinationResolver.Resolution.denied(Result.INVALID_REQUEST);
            }
            return result;
        } catch (Exception unavailable) { return DestinationResolver.Resolution.denied(Result.UNAVAILABLE); }
    }

    /** Consume coordinator cancellation/rejection for this connection; a started teleport cannot be undone. */
    public boolean receive(UUID requestId, ApplicationMessage message) {
        Entry entry;
        Result reason;
        synchronized (lock) {
            entry = requests.get(requestId);
            if (closed || entry == null || entry.state == State.TERMINAL || entry.state == State.TELEPORTING) return false;
            if (message instanceof CancelHandoff cancel && cancel.handoffId().equals(entry.binding.handoffId())) reason = cancel.reason();
            else if (message instanceof ApplicationMessage.Error error) reason = error.reason();
            else if (message instanceof HandoffRejected rejected) reason = rejected.reason();
            else return false;
        }
        finish(entry, reason, false, false); return true;
    }
    public void maintain() {
        List<Entry> expired = new ArrayList<>();
        synchronized (lock) {
            long now = nanos.getAsLong();
            Iterator<Entry> iterator = requests.values().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (entry.state == State.TERMINAL && now - entry.terminalAt >= limits.retentionMillis() * 1_000_000L) {
                    iterator.remove(); retained -= entry.bytes;
                } else if (entry.state != State.TERMINAL && entry.state != State.TELEPORTING && expired(entry)) expired.add(entry);
            }
        }
        expired.forEach(entry -> finish(entry, Result.EXPIRED, true));
    }
    public Stats stats() { synchronized (lock) { return new Stats(requests.size(), players.size(), retained, closed); } }
    @Override public void close() {
        List<Entry> pending;
        synchronized (lock) {
            if (closed) return;
            closed = true; pending = List.copyOf(players.values());
        }
        pending.forEach(entry -> finish(entry, Result.UNAVAILABLE, false));
        synchronized (lock) { requests.clear(); players.clear(); retained = 0; }
    }
    private void finish(Entry entry, Result result, boolean notify) {
        finish(entry, result, notify, true);
    }
    private void finish(Entry entry, Result result, boolean notify, boolean allowStarted) {
        boolean send;
        synchronized (lock) {
            if (entry.state == State.TERMINAL || !allowStarted && entry.state == State.TELEPORTING) return;
            entry.player = null;
            entry.state = State.TERMINAL; entry.result = result; entry.terminalAt = nanos.getAsLong();
            players.remove(entry.binding.playerId(), entry); send = notify && !closed;
        }
        boolean queued = false;
        if (send) {
            ApplicationMessage message = entry.claimed ? new CompleteHandoff(entry.binding.handoffId(), entry.binding.playerId(), localId, result)
                    : new CancelHandoff(entry.binding.handoffId(), result == Result.SUCCESS ? Result.CANCELLED : result);
            try { queued = link.send(entry.requestId, message); } catch (RuntimeException unavailable) { /* No retry of player work. */ }
        }
        TeleportCoordinatorLog.BACKEND.info("arrival_finished server={} request={} player={} result={}",
                TeleportCoordinatorLog.safe(localId.value()), entry.requestId, entry.binding.playerId(), result);
        if (!entry.preparation.isDone()) entry.preparation.complete(new HandoffRejected(result));
        entry.arrival.complete(new ArrivalResult(result, queued));
    }
    private boolean expired(Entry entry) { return nanos.getAsLong() - entry.deadline >= 0; }
    private static boolean matches(HandoffBinding local, HandoffBinding claimed) {
        return local.handoffId().equals(claimed.handoffId()) && local.playerId().equals(claimed.playerId())
                && local.source().equals(claimed.source()) && local.target().equals(claimed.target()) && local.action() == claimed.action()
                && claimed.expiresAtEpochMillis() <= local.expiresAtEpochMillis();
    }
    private static void requireId(UUID id) {
        Objects.requireNonNull(id);
        if (id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil UUID");
    }
}
