package _959.server_waypoint.core.restore;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded, temporary, single-use storage for removed waypoint snapshots. */
public final class WaypointRestoreRegistry<O> {
    public static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(10);
    public static final int DEFAULT_PER_OWNER_CAPACITY = 8;
    public static final int DEFAULT_GLOBAL_CAPACITY = 256;
    private static final int TOKEN_BYTES = 18;

    private final Clock clock;
    private final Duration expiry;
    private final int perOwnerCapacity;
    private final int globalCapacity;
    private final SecureRandom random;
    private final LinkedHashMap<String, StoredEntry<O>> entries = new LinkedHashMap<>();

    public WaypointRestoreRegistry() {
        this(
                Clock.systemUTC(),
                DEFAULT_EXPIRY,
                DEFAULT_PER_OWNER_CAPACITY,
                DEFAULT_GLOBAL_CAPACITY,
                new SecureRandom()
        );
    }

    public WaypointRestoreRegistry(
            Clock clock,
            Duration expiry,
            int perOwnerCapacity,
            int globalCapacity,
            SecureRandom random
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        if (expiry.isNegative() || expiry.isZero()) {
            throw new IllegalArgumentException("expiry must be positive");
        }
        if (perOwnerCapacity < 1 || globalCapacity < 1) {
            throw new IllegalArgumentException("capacities must be positive");
        }
        this.perOwnerCapacity = perOwnerCapacity;
        this.globalCapacity = globalCapacity;
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized String register(
            O owner,
            String dimensionName,
            String listIdentifier,
            SimpleWaypoint waypoint
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        Objects.requireNonNull(waypoint, "waypoint");
        this.removeExpired();
        this.trimOwner(owner);
        while (this.entries.size() >= this.globalCapacity) {
            this.entries.remove(this.entries.keySet().iterator().next());
        }
        String token;
        do {
            byte[] bytes = new byte[TOKEN_BYTES];
            this.random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (this.entries.containsKey(token));
        this.entries.put(token, new StoredEntry<>(
                owner,
                dimensionName,
                listIdentifier,
                new SimpleWaypoint(waypoint),
                this.clock.instant().plus(this.expiry)
        ));
        return token;
    }

    public synchronized Optional<Entry> lookup(O owner, String token) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(token, "token");
        this.removeExpired();
        StoredEntry<O> entry = this.entries.get(token);
        if (entry == null || !entry.owner().equals(owner)) {
            return Optional.empty();
        }
        return Optional.of(entry.detached());
    }

    public synchronized boolean consume(O owner, String token) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(token, "token");
        this.removeExpired();
        StoredEntry<O> entry = this.entries.get(token);
        if (entry == null || !entry.owner().equals(owner)) {
            return false;
        }
        this.entries.remove(token);
        return true;
    }

    public synchronized int size() {
        this.removeExpired();
        return this.entries.size();
    }

    private void trimOwner(O owner) {
        List<String> ownerTokens = new ArrayList<>();
        for (Map.Entry<String, StoredEntry<O>> entry : this.entries.entrySet()) {
            if (entry.getValue().owner().equals(owner)) {
                ownerTokens.add(entry.getKey());
            }
        }
        int removeCount = ownerTokens.size() - this.perOwnerCapacity + 1;
        for (int index = 0; index < removeCount; index++) {
            this.entries.remove(ownerTokens.get(index));
        }
    }

    private void removeExpired() {
        Instant now = this.clock.instant();
        Iterator<StoredEntry<O>> iterator = this.entries.values().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().expiresAt().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    public record Entry(
            String dimensionName,
            String listIdentifier,
            SimpleWaypoint waypoint,
            Instant expiresAt
    ) {
        public Entry {
            waypoint = new SimpleWaypoint(waypoint);
        }
    }

    private record StoredEntry<O>(
            O owner,
            String dimensionName,
            String listIdentifier,
            SimpleWaypoint waypoint,
            Instant expiresAt
    ) {
        private Entry detached() {
            return new Entry(this.dimensionName, this.listIdentifier, this.waypoint, this.expiresAt);
        }
    }
}
