package _959.server_waypoint.navigation;

/** Per-player marker counters since enabling. Counts packets, not bytes. */
public record WorldMarkerStatistics(long updates, long spawns, long moves, long metadata, long removals,
                                    long updateNanos) {
}
