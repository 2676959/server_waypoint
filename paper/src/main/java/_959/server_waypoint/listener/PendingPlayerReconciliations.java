package _959.server_waypoint.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Coalesces deferred inventory passes without holding its lock during player or scheduler work. */
final class PendingPlayerReconciliations {
    private final Map<UUID, Pending> players = new HashMap<>();
    private boolean closed;

    @FunctionalInterface
    interface Scheduler {
        boolean schedule(Runnable action, Runnable retired);
    }

    void request(UUID playerId, boolean restoreMissing, Scheduler scheduler, Consumer<Boolean> reconcile) {
        Pending pending;
        synchronized (this) {
            if (this.closed) {
                return;
            }
            pending = this.players.get(playerId);
            if (pending != null) {
                pending.restoreMissing |= restoreMissing;
                pending.requested = true;
                return;
            }
            pending = new Pending(playerId, restoreMissing, scheduler, reconcile);
            this.players.put(playerId, pending);
        }
        this.schedule(pending);
    }

    synchronized void remove(UUID playerId) {
        this.players.remove(playerId);
    }

    synchronized void close() {
        this.closed = true;
        this.players.clear();
    }

    private void schedule(Pending pending) {
        synchronized (this) {
            if (this.players.get(pending.playerId) != pending) {
                return;
            }
        }
        try {
            if (!pending.scheduler.schedule(() -> this.run(pending), () -> this.retire(pending))) {
                this.retire(pending);
            }
        } catch (RuntimeException exception) {
            this.retire(pending);
            throw exception;
        }
    }

    private synchronized void retire(Pending pending) {
        // An old task must never remove a new connection's entry for the same UUID.
        this.players.remove(pending.playerId, pending);
    }

    private void run(Pending pending) {
        boolean restoreMissing;
        synchronized (this) {
            if (this.players.get(pending.playerId) != pending) {
                return;
            }
            restoreMissing = pending.restoreMissing;
            pending.restoreMissing = false;
            pending.requested = false;
        }
        try {
            pending.reconcile.accept(restoreMissing);
        } finally {
            boolean runAgain = false;
            synchronized (this) {
                if (this.players.get(pending.playerId) == pending) {
                    runAgain = pending.requested;
                    if (!runAgain) {
                        this.players.remove(pending.playerId);
                    }
                }
            }
            if (runAgain) {
                this.schedule(pending);
            }
        }
    }

    private static final class Pending {
        private final UUID playerId;
        private final Scheduler scheduler;
        private final Consumer<Boolean> reconcile;
        private boolean restoreMissing;
        private boolean requested;

        private Pending(UUID playerId, boolean restoreMissing, Scheduler scheduler, Consumer<Boolean> reconcile) {
            this.playerId = playerId;
            this.restoreMissing = restoreMissing;
            this.scheduler = scheduler;
            this.reconcile = reconcile;
        }
    }
}
