package _959.server_waypoint.common.client.gui.screens;

import java.util.concurrent.TimeUnit;

/** Tracks one edit request without relying on a wall clock. */
final class EditResponseDeadline {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private long requestId = -1;
    private long deadlineNanos;

    boolean pending() {
        return this.requestId >= 0;
    }

    long requestId() {
        return this.requestId;
    }

    void begin(long requestId, long nowNanos) {
        if (requestId < 0) {
            throw new IllegalArgumentException("Edit request ID must be non-negative");
        }
        if (this.pending()) {
            throw new IllegalStateException("An edit request is already pending");
        }
        this.requestId = requestId;
        this.deadlineNanos = nowNanos + TIMEOUT_NANOS;
    }

    boolean clearIfMatches(long requestId) {
        if (this.requestId != requestId) {
            return false;
        }
        this.clear();
        return true;
    }

    boolean expire(long nowNanos) {
        if (!this.pending() || nowNanos - this.deadlineNanos < 0) {
            return false;
        }
        this.clear();
        return true;
    }

    void clear() {
        this.requestId = -1;
        this.deadlineNanos = 0;
    }
}
