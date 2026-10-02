package _959.server_waypoint.crossserver.transport;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded, secret-free counters. Failures include admission/registration/read/write failures. */
public final class ConnectionMetrics {
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong registrations = new AtomicLong();
    private final AtomicLong disconnects = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong sentHeartbeats = new AtomicLong();
    private final AtomicLong receivedHeartbeats = new AtomicLong();
    public record Snapshot(long attempts, long registrations, long disconnects, long failures,
                           long sentHeartbeats, long receivedHeartbeats) { }
    private static void increment(AtomicLong counter) { counter.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1); }
    public void attempted() { increment(attempts); }
    public void registered() { increment(registrations); }
    public void disconnected() { increment(disconnects); }
    public void failed() { increment(failures); }
    public void sentHeartbeat() { increment(sentHeartbeats); }
    public void receivedHeartbeat() { increment(receivedHeartbeats); }
    public Snapshot snapshot() { return new Snapshot(attempts.get(), registrations.get(), disconnects.get(), failures.get(), sentHeartbeats.get(), receivedHeartbeats.get()); }
}
