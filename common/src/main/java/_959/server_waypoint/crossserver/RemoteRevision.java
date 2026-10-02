package _959.server_waypoint.crossserver;

/** Non-negative catalog or list revision. Compare only within the same identity and revision lifetime. */
public record RemoteRevision(long value) implements Comparable<RemoteRevision> {
    public RemoteRevision {
        if (value < 0) {
            throw new IllegalArgumentException("Revision must be non-negative");
        }
    }

    /** Exhaustion is terminal; revisions must never wrap back to zero. */
    public RemoteRevision next() {
        return new RemoteRevision(Math.incrementExact(value));
    }

    @Override
    public int compareTo(RemoteRevision other) {
        return Long.compare(value, other.value);
    }
}
