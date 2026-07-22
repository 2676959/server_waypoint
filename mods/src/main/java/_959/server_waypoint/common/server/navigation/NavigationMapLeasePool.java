package _959.server_waypoint.common.server.navigation;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks map IDs and protects entries leased by active player sessions from
 * reuse. The pool grows only when every remembered ID is active.
 */
final class NavigationMapLeasePool<K> {
    private final LinkedHashMap<K, Integer> mapIds = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<UUID, K> activeKeys = new LinkedHashMap<>();

    @Nullable Integer idFor(K key) {
        return this.mapIds.get(Objects.requireNonNull(key, "key"));
    }

    void remember(K key, int mapId) {
        this.mapIds.put(Objects.requireNonNull(key, "key"), mapId);
    }

    void forget(K key) {
        Objects.requireNonNull(key, "key");
        if (this.isActive(key)) {
            throw new IllegalStateException("Cannot forget an actively leased navigation map");
        }
        this.mapIds.remove(key);
    }

    @Nullable ReusableEntry<K> removeOldestInactive() {
        var iterator = this.mapIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Integer> entry = iterator.next();
            if (!this.isActive(entry.getKey())) {
                ReusableEntry<K> reusable = new ReusableEntry<>(entry.getKey(), entry.getValue());
                iterator.remove();
                return reusable;
            }
        }
        return null;
    }

    void activate(UUID playerUuid, K key) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(key, "key");
        if (!this.mapIds.containsKey(key)) {
            throw new IllegalStateException("Cannot lease an unknown navigation map");
        }
        this.activeKeys.put(playerUuid, key);
        this.mapIds.get(key);
    }

    void release(UUID playerUuid) {
        this.activeKeys.remove(Objects.requireNonNull(playerUuid, "playerUuid"));
    }

    boolean isActive(K key) {
        return this.activeKeys.containsValue(key);
    }

    Map<K, Integer> entries() {
        return new LinkedHashMap<>(this.mapIds);
    }

    void clear() {
        this.activeKeys.clear();
        this.mapIds.clear();
    }

    record ReusableEntry<K>(K key, int mapId) {
    }
}
