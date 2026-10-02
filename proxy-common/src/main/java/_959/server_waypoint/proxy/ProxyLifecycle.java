package _959.server_waypoint.proxy;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/** Adapter scheduling/events with no proxy scheduler, event, or plugin types in policy code. */
public interface ProxyLifecycle {
    /** Registers a startup callback; registration is owned and cancelled by the caller. */
    Registration onStart(Runnable callback);

    /** Registers a shutdown callback, before the adapter tears down its owned resources. */
    Registration onStop(Runnable callback);

    Registration onPlayerDisconnect(Consumer<UUID> callback);

    /** Schedules one nonblocking callback with a nonnegative delay; zero means queued, not inline. */
    Registration schedule(Duration delay, Runnable callback);

    /** Idempotently unregisters/cancels future invocations; does not interrupt an active callback. */
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
