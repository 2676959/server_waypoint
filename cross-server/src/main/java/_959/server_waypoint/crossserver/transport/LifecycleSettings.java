package _959.server_waypoint.crossserver.transport;

/** Worker policy, independent of channel byte/object ceilings. */
public record LifecycleSettings(boolean enabled, int heartbeatMillis, int reconnectMinMillis,
                                int reconnectMaxMillis) {
    public static final LifecycleSettings DEFAULT = new LifecycleSettings(false, 5000, 1000, 30_000);
    public LifecycleSettings {
        if (heartbeatMillis < 1 || heartbeatMillis > 60_000 || reconnectMinMillis < 1
                || reconnectMaxMillis < reconnectMinMillis || reconnectMaxMillis > 300_000) {
            throw new IllegalArgumentException("Invalid lifecycle settings");
        }
    }
    public void validate(TcpLimits limits) {
        if ((long) heartbeatMillis * 2 >= limits.operationMillis()) throw new IllegalArgumentException("Heartbeat must fit disconnect timeout");
    }
    /** Capped exponential delay. Stable connections reset the failure counter. */
    public long reconnectDelay(int failures) {
        long delay = reconnectMinMillis;
        for (int n = 1; n < failures && delay < reconnectMaxMillis; n++) delay = Math.min(reconnectMaxMillis, delay * 2);
        return delay;
    }
}
