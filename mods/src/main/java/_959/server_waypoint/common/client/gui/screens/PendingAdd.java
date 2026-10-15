package _959.server_waypoint.common.client.gui.screens;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the waypoint an Add form has asked the server for, without a wall clock. There is no
 * correlated reply to /wp add, so the screen watches for the waypoint to appear in the synced data
 * and gives up after {@link #TIMEOUT_NANOS}.
 */
final class PendingAdd {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private @Nullable String dimension;
    private @Nullable String list;
    private @Nullable String name;
    private long deadlineNanos;

    boolean pending() {
        return this.name != null;
    }

    /** The dimension of the pending waypoint; only while {@link #pending()}. */
    String dimension() {
        return this.required(this.dimension);
    }

    /** The list of the pending waypoint; only while {@link #pending()}. */
    String list() {
        return this.required(this.list);
    }

    /** The name of the pending waypoint; only while {@link #pending()}. */
    String name() {
        return this.required(this.name);
    }

    void begin(String dimension, String list, String name, long nowNanos) {
        if (this.pending()) {
            throw new IllegalStateException("A waypoint is already being added");
        }
        this.dimension = dimension;
        this.list = list;
        this.name = name;
        this.deadlineNanos = nowNanos + TIMEOUT_NANOS;
    }

    /** Ends the wait once its deadline has come; true only for the call that ends it. */
    boolean expire(long nowNanos) {
        if (!this.pending() || nowNanos - this.deadlineNanos < 0) {
            return false;
        }
        this.clear();
        return true;
    }

    void clear() {
        this.dimension = null;
        this.list = null;
        this.name = null;
        this.deadlineNanos = 0;
    }

    private String required(@Nullable String value) {
        if (value == null) {
            throw new IllegalStateException("No waypoint is being added");
        }
        return value;
    }
}
