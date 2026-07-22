package _959.server_waypoint.navigation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PaperNavigationMapCache implements AutoCloseable {
    private static final String CACHE_FILE_NAME = "navigation-map-cache.json";
    private static final MapView.Scale MAP_SCALE = MapView.Scale.NORMAL;
    private static final int BLOCKS_PER_PIXEL = 1 << MAP_SCALE.ordinal();
    private static final int MAP_SPAN = 128 * BLOCKS_PER_PIXEL;
    private static final int MAX_INACTIVE_MAPS = 32;

    private final JavaPlugin plugin;
    private final Server server;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path cacheFile;
    private final Map<MapCacheKey, CachedMap> maps = new HashMap<>();
    private long accessSequence;
    private boolean closed;

    public PaperNavigationMapCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.cacheFile = plugin.getDataFolder().toPath().resolve(CACHE_FILE_NAME);
        this.load();
    }

    public @Nullable Lease acquire(UUID playerUuid, NavigationTarget target) {
        this.assertOpenServerThread();
        World world = this.resolveWorld(target.dimensionName());
        if (world == null) {
            return null;
        }

        MapCacheKey key = MapCacheKey.from(target);
        CachedMap cached = this.maps.get(key);
        if (cached == null) {
            cached = this.findReusable(world);
            if (cached == null) {
                cached = this.create(world, key);
            } else {
                this.maps.remove(cached.key);
                configure(cached.view, key);
                cached.renderer.clearTargets();
                cached.key = key;
            }
            this.maps.put(key, cached);
        }

        cached.activeLeases++;
        cached.lastAccess = this.nextAccess();
        cached.renderer.setTarget(
                playerUuid,
                target,
                key.centerX(),
                key.centerZ(),
                BLOCKS_PER_PIXEL
        );
        this.save();
        return new Lease(this, cached, playerUuid, key);
    }

    @Override
    public void close() {
        this.assertServerThread();
        if (this.closed) {
            return;
        }
        this.save();
        this.closed = true;
        for (CachedMap cached : this.maps.values()) {
            cached.renderer.clearTargets();
            cached.view.removeRenderer(cached.renderer);
        }
        this.maps.clear();
    }

    private CachedMap create(World world, MapCacheKey key) {
        MapView view = this.server.createMap(world);
        configure(view, key);
        PaperNavigationMapRenderer renderer = new PaperNavigationMapRenderer();
        view.addRenderer(renderer);
        return new CachedMap(key, view, renderer, 0, this.nextAccess());
    }

    private @Nullable CachedMap findReusable(World world) {
        return this.maps.values().stream()
                .filter(cached -> cached.activeLeases == 0)
                .filter(cached -> sameWorld(cached.view, world))
                .min(Comparator.comparingLong(cached -> cached.lastAccess))
                .orElse(null);
    }

    private void updateTarget(Lease lease, NavigationTarget target) {
        this.assertOpenServerThread();
        lease.assertOpen();
        MapCacheKey key = MapCacheKey.from(target);
        if (!lease.key.equals(key) || !lease.cached.key.equals(lease.key)) {
            throw new IllegalArgumentException("Navigation target no longer belongs to this map lease");
        }
        lease.cached.lastAccess = this.nextAccess();
        lease.cached.renderer.setTarget(
                lease.playerUuid,
                target,
                key.centerX(),
                key.centerZ(),
                BLOCKS_PER_PIXEL
        );
    }

    private void release(Lease lease) {
        this.assertServerThread();
        if (lease.closed) {
            return;
        }
        lease.closed = true;
        if (this.closed) {
            return;
        }
        CachedMap cached = lease.cached;
        cached.renderer.removeTarget(lease.playerUuid);
        if (cached.activeLeases < 1) {
            throw new IllegalStateException("Navigation map lease count underflow");
        }
        cached.activeLeases--;
        cached.lastAccess = this.nextAccess();
        this.trimInactiveMaps();
        this.save();
    }

    private void trimInactiveMaps() {
        while (this.inactiveMapCount() > MAX_INACTIVE_MAPS) {
            CachedMap oldest = this.maps.values().stream()
                    .filter(cached -> cached.activeLeases == 0)
                    .min(Comparator.comparingLong(cached -> cached.lastAccess))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            this.maps.remove(oldest.key);
            oldest.renderer.clearTargets();
            oldest.view.removeRenderer(oldest.renderer);
        }
    }

    private int inactiveMapCount() {
        int count = 0;
        for (CachedMap cached : this.maps.values()) {
            if (cached.activeLeases == 0) {
                count++;
            }
        }
        return count;
    }

    private void load() {
        this.assertServerThread();
        if (!Files.isRegularFile(this.cacheFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(this.cacheFile)) {
            PersistedCache persisted = this.gson.fromJson(reader, PersistedCache.class);
            if (persisted == null || persisted.entries == null) {
                return;
            }
            Set<Integer> loadedMapIds = new HashSet<>();
            for (PersistedEntry entry : persisted.entries) {
                if (entry == null || entry.key == null || !loadedMapIds.add(entry.mapId)) {
                    continue;
                }
                MapView view = this.server.getMap(entry.mapId);
                World world = this.resolveWorld(entry.key.dimensionName());
                if (view == null || world == null || !sameWorld(view, world)) {
                    continue;
                }
                configure(view, entry.key);
                PaperNavigationMapRenderer renderer = new PaperNavigationMapRenderer();
                view.addRenderer(renderer);
                CachedMap previous = this.maps.putIfAbsent(
                        entry.key,
                        new CachedMap(entry.key, view, renderer, 0, entry.lastAccess)
                );
                if (previous != null) {
                    view.removeRenderer(renderer);
                    continue;
                }
                this.accessSequence = Math.max(this.accessSequence, entry.lastAccess);
            }
            this.trimInactiveMaps();
        } catch (IOException | RuntimeException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Could not load navigation map cache", exception);
        }
    }

    private void save() {
        try {
            Files.createDirectories(this.cacheFile.getParent());
            List<PersistedEntry> entries = new ArrayList<>(this.maps.size());
            for (CachedMap cached : this.maps.values()) {
                entries.add(new PersistedEntry(
                        cached.key,
                        cached.view.getId(),
                        cached.lastAccess
                ));
            }
            try (Writer writer = Files.newBufferedWriter(this.cacheFile)) {
                this.gson.toJson(new PersistedCache(entries), writer);
            }
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save navigation map cache", exception);
        }
    }

    private @Nullable World resolveWorld(String dimensionName) {
        NamespacedKey key = NamespacedKey.fromString(dimensionName);
        return key == null ? null : this.server.getWorld(key);
    }

    private static void configure(MapView view, MapCacheKey key) {
        view.setCenterX(key.centerX());
        view.setCenterZ(key.centerZ());
        view.setScale(key.scale());
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
    }

    private static boolean sameWorld(MapView view, World world) {
        return view.getWorld() != null && view.getWorld().getUID().equals(world.getUID());
    }

    private long nextAccess() {
        return ++this.accessSequence;
    }

    private void assertOpenServerThread() {
        this.assertServerThread();
        if (this.closed) {
            throw new IllegalStateException("Navigation map cache is closed");
        }
    }

    private void assertServerThread() {
        if (!this.server.isPrimaryThread()) {
            throw new IllegalStateException("Navigation map cache must run on the Paper server thread");
        }
    }

    private record MapCacheKey(
            String dimensionName,
            int centerX,
            int centerZ,
            MapView.Scale scale
    ) {
        private static MapCacheKey from(NavigationTarget target) {
            return new MapCacheKey(
                    target.dimensionName(),
                    normalizedCenter(target.position().x()),
                    normalizedCenter(target.position().z()),
                    MAP_SCALE
            );
        }

        private static int normalizedCenter(int coordinate) {
            return Math.floorDiv(coordinate + MAP_SPAN / 2, MAP_SPAN) * MAP_SPAN;
        }
    }

    private static final class CachedMap {
        private MapCacheKey key;
        private final MapView view;
        private final PaperNavigationMapRenderer renderer;
        private int activeLeases;
        private long lastAccess;

        private CachedMap(
                MapCacheKey key,
                MapView view,
                PaperNavigationMapRenderer renderer,
                int activeLeases,
                long lastAccess
        ) {
            this.key = key;
            this.view = view;
            this.renderer = renderer;
            this.activeLeases = activeLeases;
            this.lastAccess = lastAccess;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final PaperNavigationMapCache owner;
        private final CachedMap cached;
        private final UUID playerUuid;
        private final MapCacheKey key;
        private boolean closed;

        private Lease(
                PaperNavigationMapCache owner,
                CachedMap cached,
                UUID playerUuid,
                MapCacheKey key
        ) {
            this.owner = owner;
            this.cached = cached;
            this.playerUuid = playerUuid;
            this.key = key;
        }

        public MapView view() {
            this.assertOpen();
            return this.cached.view;
        }

        public boolean matches(NavigationTarget target) {
            this.assertOpen();
            return this.key.equals(MapCacheKey.from(target));
        }

        public void updateTarget(NavigationTarget target) {
            this.owner.updateTarget(this, target);
        }

        @Override
        public void close() {
            this.owner.release(this);
        }

        private void assertOpen() {
            if (this.closed) {
                throw new IllegalStateException("Navigation map lease is closed");
            }
        }
    }

    private static final class PersistedCache {
        private List<PersistedEntry> entries;

        @SuppressWarnings("unused")
        private PersistedCache() {
        }

        private PersistedCache(List<PersistedEntry> entries) {
            this.entries = entries;
        }
    }

    private static final class PersistedEntry {
        private MapCacheKey key;
        private int mapId;
        private long lastAccess;

        @SuppressWarnings("unused")
        private PersistedEntry() {
        }

        private PersistedEntry(MapCacheKey key, int mapId, long lastAccess) {
            this.key = key;
            this.mapId = mapId;
            this.lastAccess = lastAccess;
        }
    }
}
