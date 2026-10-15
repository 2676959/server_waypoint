package _959.server_waypoint.proxy.handoff;

/** Active and terminal records share the retention budget; admission never evicts replay protection. */
public record HandoffLimits(int records, int active, int perSource, long retainedBytes,
                            int auditEntries, long expiryMillis, long retentionMillis) {
    public static final HandoffLimits DEFAULT = new HandoffLimits(4096, 256, 64, 16 * 1024 * 1024,
            256, 15_000, 60_000);

    public HandoffLimits {
        if (records < 1 || records > 65_536 || active < 1 || active > records || perSource < 1 || perSource > active
                || retainedBytes < 2048 || retainedBytes > 64 * 1024 * 1024L
                || auditEntries < 1 || auditEntries > 4096 || expiryMillis < 1 || expiryMillis > 15_000
                || retentionMillis < expiryMillis || retentionMillis > 600_000) {
            throw new IllegalArgumentException("Invalid handoff limits");
        }
    }
}
