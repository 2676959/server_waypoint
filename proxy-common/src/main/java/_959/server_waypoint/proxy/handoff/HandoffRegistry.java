package _959.server_waypoint.proxy.handoff;

import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Coordinator-owned atomic state only. No platform objects, network sends or platform callbacks under its lock.
 * Access through HandoffRequestHandler for live peer, proxy-player and admission checks.
 */
public final class HandoffRegistry implements AutoCloseable {
    public enum State { PREPARING, PREPARED, CLAIMED, COMPLETED, CANCELLED, REJECTED, EXPIRED }
    public enum Event { PREPARE, RESERVE, CLAIM, COMPLETE, CANCEL, REJECT, EXPIRE, DISCONNECT, CLOSE }
    public record Snapshot(UUID requestId, PrepareHandoff request, HandoffPeer source, HandoffPeer destination,
                           HandoffBinding binding, State state, Result result, long expiresAtEpochMillis) { }
    public record Outcome(Result result, Snapshot snapshot, boolean changed) { }
    /** Deliberately excludes player IDs, request/handoff IDs, waypoint text, keys and exception messages. */
    public record Audit(long sequence, Event event, Result result) { }
    public record Stats(int records, int active, long retainedBytes, int auditEntries, boolean closed) { }

    private static final class Entry {
        final UUID requestId;
        final PrepareHandoff request;
        final HandoffPeer source, destination;
        final long bytes, expiresAtEpochMillis;
        long deadlineNanos, terminalAtNanos;
        HandoffBinding binding;
        boolean transferStarted;
        State state = State.PREPARING;
        Result result = Result.SUCCESS;
        Entry(UUID requestId, PrepareHandoff request, HandoffPeer source, HandoffPeer destination,
              long bytes, long expiresAtEpochMillis, long deadlineNanos) {
            this.requestId = requestId; this.request = request; this.source = source; this.destination = destination;
            this.bytes = bytes; this.expiresAtEpochMillis = expiresAtEpochMillis; this.deadlineNanos = deadlineNanos;
        }
        boolean active() { return state == State.PREPARING || state == State.PREPARED || state == State.CLAIMED; }
        Snapshot snapshot() { return new Snapshot(requestId, request, source, destination, binding, state, result, expiresAtEpochMillis); }
    }
    private final HandoffLimits limits;
    private final LongSupplier nanos, epochMillis;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, UUID> players = new HashMap<>(), handoffs = new HashMap<>();
    private final Deque<Audit> audit = new ArrayDeque<>();
    private long retainedBytes, auditSequence;
    private boolean closed;

    public HandoffRegistry(HandoffLimits limits) { this(limits, System::nanoTime, System::currentTimeMillis); }
    public HandoffRegistry(HandoffLimits limits, LongSupplier nanos, LongSupplier epochMillis) {
        this.limits = Objects.requireNonNull(limits);
        this.nanos = Objects.requireNonNull(nanos);
        this.epochMillis = Objects.requireNonNull(epochMillis);
    }

    public synchronized Outcome prepare(UUID requestId, PrepareHandoff request, HandoffPeer source, HandoffPeer destination) {
        requireId(requestId);
        Objects.requireNonNull(request); Objects.requireNonNull(source); Objects.requireNonNull(destination);
        sweep();
        if (closed) return denied(Event.PREPARE, Result.UNAVAILABLE);
        if (!source.serverId().equals(request.source())) return denied(Event.PREPARE, Result.WRONG_SOURCE);
        if (!destination.serverId().equals(request.target().serverId())) return denied(Event.PREPARE, Result.WRONG_DESTINATION);
        if (entries.containsKey(requestId)) return denied(Event.PREPARE, Result.REPLAY);
        long bytes = 2048L + 2L * ((long) request.target().dimensionName().length()
                + request.target().listName().length() + request.target().waypointName().length());
        long sourceRecords = entries.values().stream().filter(e -> e.source.serverId().equals(source.serverId())).count();
        if (players.containsKey(request.playerId()) || players.size() >= limits.active() || sourceRecords >= limits.perSource()
                || entries.size() >= limits.records() || bytes > limits.retainedBytes() - retainedBytes) {
            return denied(Event.PREPARE, Result.BUSY);
        }
        long expiry;
        try { expiry = Math.addExact(epochMillis.getAsLong(), limits.expiryMillis()); }
        catch (ArithmeticException invalidClock) { return denied(Event.PREPARE, Result.UNAVAILABLE); }
        if (expiry <= 0) return denied(Event.PREPARE, Result.UNAVAILABLE);
        Entry entry = new Entry(requestId, request, source, destination, bytes, expiry,
                nanos.getAsLong() + limits.expiryMillis() * 1_000_000L);
        entries.put(requestId, entry); players.put(request.playerId(), requestId); retainedBytes += bytes;
        return changed(Event.PREPARE, entry);
    }

    /** Accept only the exact destination's preparation confirmation; it may shorten, never extend, expiry. */
    public synchronized Outcome reserve(HandoffPeer peer, UUID requestId, HandoffBinding binding) {
        Objects.requireNonNull(binding);
        Entry entry = current(requestId);
        if (entry == null) return denied(Event.RESERVE, Result.NOT_FOUND);
        if (!entry.destination.equals(peer)) return denied(Event.RESERVE, Result.WRONG_DESTINATION);
        if (!entry.active()) return denied(Event.RESERVE, terminalReason(entry));
        if (entry.state != State.PREPARING) return denied(Event.RESERVE, Result.REPLAY);
        PrepareHandoff request = entry.request;
        if (!request.playerId().equals(binding.playerId()) || !request.source().equals(binding.source())
                || !request.target().equals(binding.target()) || request.action() != binding.action()
                || handoffs.containsKey(binding.handoffId())) {
            return denied(Event.RESERVE, Result.INVALID_REQUEST);
        }
        long expires = Math.min(binding.expiresAtEpochMillis(), entry.expiresAtEpochMillis);
        long remaining = expires - epochMillis.getAsLong();
        if (remaining <= 0) {
            finish(entry, State.EXPIRED, Result.EXPIRED, Event.EXPIRE);
            return new Outcome(Result.EXPIRED, entry.snapshot(), true);
        }
        // Bound independently of wall-clock rollback and overflow.
        remaining = Math.min(remaining, limits.expiryMillis());
        long now = nanos.getAsLong();
        entry.deadlineNanos = now + Math.min(entry.deadlineNanos - now, remaining * 1_000_000L);
        entry.binding = new HandoffBinding(binding.handoffId(), binding.playerId(), binding.source(),
                binding.target(), binding.action(), expires);
        entry.state = State.PREPARED;
        handoffs.put(binding.handoffId(), requestId);
        return changed(Event.RESERVE, entry);
    }

    /** Records the exact source session's final readiness once, within the bounded record. */
    public synchronized Result beginTransfer(HandoffPeer source, UUID requestId, HandoffBinding binding) {
        Entry entry = current(requestId);
        if (entry == null) return Result.NOT_FOUND;
        if (!entry.active()) return terminalReason(entry);
        if (!entry.source.equals(source) || !Objects.equals(entry.binding, binding)) return Result.INVALID_REQUEST;
        if (entry.transferStarted) return Result.REPLAY;
        if (entry.state != State.PREPARED) return Result.INVALID_REQUEST;
        entry.transferStarted = true;
        return Result.SUCCESS;
    }

    public synchronized boolean transferStarted(UUID requestId) {
        Entry entry = current(requestId);
        return entry != null && entry.active() && entry.transferStarted;
    }

    synchronized Set<UUID> activeTransferRequestIds() {
        sweep();
        Set<UUID> active = new HashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.active() && entry.transferStarted) active.add(entry.requestId);
        }
        return active;
    }

    public synchronized Outcome claim(HandoffPeer peer, UUID requestId, ClaimHandoff claim) {
        Entry entry = current(requestId);
        Outcome invalid = validate(entry, peer, claim.handoffId(), claim.playerId(), claim.destination());
        if (invalid != null) return invalid;
        if (!entry.active()) return denied(Event.CLAIM, terminalReason(entry));
        if (entry.state != State.PREPARED) return denied(Event.CLAIM, Result.REPLAY);
        entry.state = State.CLAIMED;
        return changed(Event.CLAIM, entry);
    }

    public synchronized Outcome complete(HandoffPeer peer, UUID requestId, CompleteHandoff complete) {
        Entry entry = current(requestId);
        Outcome invalid = validate(entry, peer, complete.handoffId(), complete.playerId(), complete.destination());
        if (invalid != null) return invalid;
        if (entry.state == State.COMPLETED && entry.result == complete.result()) {
            return new Outcome(Result.SUCCESS, entry.snapshot(), false);
        }
        if (!entry.active()) return denied(Event.COMPLETE, terminalReason(entry));
        if (entry.state != State.CLAIMED) return denied(Event.COMPLETE, Result.INVALID_REQUEST);
        finish(entry, State.COMPLETED, complete.result(), Event.COMPLETE);
        return new Outcome(Result.SUCCESS, entry.snapshot(), true);
    }

    public synchronized Outcome cancel(HandoffPeer peer, UUID requestId, CancelHandoff cancel) {
        Entry entry = current(requestId);
        if (entry == null || entry.binding == null || !entry.binding.handoffId().equals(cancel.handoffId())) {
            return denied(Event.CANCEL, Result.NOT_FOUND);
        }
        if (!entry.source.equals(peer) && !entry.destination.equals(peer)) return denied(Event.CANCEL, Result.UNAUTHORIZED);
        // Once claimed, only the destination may finish/cancel work it now owns.
        if (entry.state == State.CLAIMED && !entry.destination.equals(peer)) return denied(Event.CANCEL, Result.WRONG_DESTINATION);
        if (entry.state == State.CANCELLED && entry.result == cancel.reason()) return new Outcome(Result.SUCCESS, entry.snapshot(), false);
        if (!entry.active()) return denied(Event.CANCEL, terminalReason(entry));
        finish(entry, State.CANCELLED, cancel.reason(), Event.CANCEL);
        return new Outcome(Result.SUCCESS, entry.snapshot(), true);
    }

    public synchronized Outcome reject(HandoffPeer peer, UUID requestId, Result reason) {
        if (reason == null || reason == Result.SUCCESS) throw new IllegalArgumentException("Expected failure");
        Entry entry = current(requestId);
        if (entry == null) return denied(Event.REJECT, Result.NOT_FOUND);
        if (!entry.destination.equals(peer)) return denied(Event.REJECT, Result.WRONG_DESTINATION);
        if (!entry.active()) return denied(Event.REJECT, terminalReason(entry));
        if (entry.state != State.PREPARING) return denied(Event.REJECT, Result.INVALID_REQUEST);
        finish(entry, State.REJECTED, reason, Event.REJECT);
        return new Outcome(Result.SUCCESS, entry.snapshot(), true);
    }

    /** Lifecycle owner calls this on disconnect/revocation, using the old session token. */
    public synchronized List<Snapshot> disconnect(HandoffPeer peer) {
        sweep();
        List<Snapshot> changed = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.active() && (entry.source.equals(peer) || entry.destination.equals(peer))) {
                finish(entry, State.CANCELLED, Result.UNAVAILABLE, Event.DISCONNECT); changed.add(entry.snapshot());
            }
        }
        return List.copyOf(changed);
    }

    /** Called only with a UUID obtained from the proxy disconnect event. */
    public synchronized List<Snapshot> disconnectPlayer(UUID playerId) {
        sweep();
        List<Snapshot> changed = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.active() && entry.request.playerId().equals(playerId)) {
                finish(entry, State.CANCELLED, Result.UNAVAILABLE, Event.DISCONNECT); changed.add(entry.snapshot());
            }
        }
        return List.copyOf(changed);
    }

    public synchronized Optional<Snapshot> find(UUID requestId) {
        Entry entry = current(requestId);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }
    public synchronized List<Snapshot> maintain() { return sweep(); }
    public synchronized void auditRejection(Result reason) { record(Event.REJECT, Objects.requireNonNull(reason)); }
    public synchronized List<Audit> audit() { return List.copyOf(audit); }
    public synchronized Stats stats() { sweep(); return new Stats(entries.size(), players.size(), retainedBytes, audit.size(), closed); }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; entries.clear(); players.clear(); handoffs.clear(); retainedBytes = 0;
        record(Event.CLOSE, Result.CANCELLED);
    }

    private Entry current(UUID requestId) { requireId(requestId); sweep(); return closed ? null : entries.get(requestId); }
    private Outcome validate(Entry entry, HandoffPeer peer, UUID handoffId, UUID playerId,
                             _959.server_waypoint.crossserver.RemoteServerId destination) {
        if (entry == null || entry.binding == null || !entry.binding.handoffId().equals(handoffId)) return denied(Event.REJECT, Result.NOT_FOUND);
        if (!entry.destination.equals(peer) || !entry.destination.serverId().equals(destination)) return denied(Event.REJECT, Result.WRONG_DESTINATION);
        if (!entry.request.playerId().equals(playerId)) return denied(Event.REJECT, Result.UNAUTHORIZED);
        return null;
    }
    private List<Snapshot> sweep() {
        long now = nanos.getAsLong();
        List<Snapshot> expired = new ArrayList<>();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.active() && now - entry.deadlineNanos >= 0) {
                finish(entry, State.EXPIRED, Result.EXPIRED, Event.EXPIRE); expired.add(entry.snapshot());
            }
            if (!entry.active() && now - entry.terminalAtNanos >= limits.retentionMillis() * 1_000_000L) {
                iterator.remove(); retainedBytes -= entry.bytes;
                if (entry.binding != null) handoffs.remove(entry.binding.handoffId());
            }
        }
        return List.copyOf(expired);
    }
    private void finish(Entry entry, State state, Result result, Event event) {
        entry.state = state; entry.result = result; entry.terminalAtNanos = nanos.getAsLong();
        players.remove(entry.request.playerId(), entry.requestId); record(event, result);
    }
    private Outcome changed(Event event, Entry entry) { record(event, Result.SUCCESS); return new Outcome(Result.SUCCESS, entry.snapshot(), true); }
    private Outcome denied(Event event, Result result) { record(event, result); return new Outcome(result, null, false); }
    private static Result terminalReason(Entry entry) { return entry.state == State.EXPIRED ? Result.EXPIRED : Result.REPLAY; }
    private void record(Event event, Result result) {
        if (audit.size() == limits.auditEntries()) audit.removeFirst();
        if (auditSequence != Long.MAX_VALUE) auditSequence++;
        audit.addLast(new Audit(auditSequence, event, result));
    }
    private static void requireId(UUID id) {
        Objects.requireNonNull(id, "requestId");
        if (id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Nil request ID");
    }
}
