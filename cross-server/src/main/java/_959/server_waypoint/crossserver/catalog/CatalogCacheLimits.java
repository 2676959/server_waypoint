package _959.server_waypoint.crossserver.catalog;

/** Canonical retained bytes, bounded identities, and monotonic stale retention. */
public record CatalogCacheLimits(int servers, int perServerBytes, long totalBytes, long staleMillis) {
    public static final CatalogCacheLimits DEFAULT = new CatalogCacheLimits(256, 2_097_152, 67_108_864, 300_000);

    public CatalogCacheLimits {
        if (servers < 1 || servers > 1024 || perServerBytes < 1 || perServerBytes > 2_097_152
                || totalBytes < perServerBytes || totalBytes > 67_108_864 || staleMillis < 1 || staleMillis > 86_400_000) {
            throw new IllegalArgumentException("Invalid catalog cache limits");
        }
    }
}
