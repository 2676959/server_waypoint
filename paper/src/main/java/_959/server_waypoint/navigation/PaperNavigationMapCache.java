package _959.server_waypoint.navigation;

import _959.server_waypoint.ModInfo;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaperNavigationMapCache implements AutoCloseable {
    private static final MapView.Scale MAP_SCALE = MapView.Scale.NORMAL;
    private static final int BLOCKS_PER_PIXEL = 1 << MAP_SCALE.ordinal();
    private static final int MAP_SPAN = 128 * BLOCKS_PER_PIXEL;

    private final Server server;
    private final NamespacedKey navigationMapIdKey;
    private final Map<UUID, CachedMap> maps = new HashMap<>();
    private boolean closed;

    public PaperNavigationMapCache(JavaPlugin plugin) {
        this.server = plugin.getServer();
        this.navigationMapIdKey = new NamespacedKey(ModInfo.MOD_ID, "navigation_map_id");
    }

    public @Nullable Lease acquire(Player player, NavigationTarget target) {
        this.assertOpenServerThread();
        World world = this.resolveWorld(target.dimensionName());
        if (world == null) {
            return null;
        }

        UUID playerUuid = player.getUniqueId();
        MapCacheKey key = MapCacheKey.from(target);
        CachedMap cached = this.maps.get(playerUuid);
        if (cached == null) {
            cached = this.loadOrCreate(player, world, key);
            this.maps.put(playerUuid, cached);
        } else {
            configure(cached.view, world, key);
        }

        cached.renderer.setTarget(
                playerUuid,
                target,
                key.centerX(),
                key.centerZ(),
                BLOCKS_PER_PIXEL
        );
        return new Lease(this, cached, playerUuid);
    }

    @Override
    public void close() {
        this.assertServerThread();
        if (this.closed) {
            return;
        }
        this.closed = true;
        for (CachedMap cached : this.maps.values()) {
            cached.renderer.clearTargets();
            cached.view.removeRenderer(cached.renderer);
        }
        this.maps.clear();
    }

    private CachedMap loadOrCreate(Player player, World world, MapCacheKey key) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Integer mapId = data.get(this.navigationMapIdKey, PersistentDataType.INTEGER);
        MapView view = mapId == null ? null : this.server.getMap(mapId);
        if (view == null) {
            view = this.server.createMap(world);
            data.set(this.navigationMapIdKey, PersistentDataType.INTEGER, view.getId());
        }
        configure(view, world, key);
        PaperNavigationMapRenderer renderer = new PaperNavigationMapRenderer();
        view.addRenderer(renderer);
        return new CachedMap(view, renderer);
    }

    private boolean updateTarget(Lease lease, NavigationTarget target) {
        this.assertOpenServerThread();
        lease.assertOpen();
        World world = this.resolveWorld(target.dimensionName());
        if (world == null) {
            return false;
        }
        MapCacheKey key = MapCacheKey.from(target);
        configure(lease.cached.view, world, key);
        lease.cached.renderer.setTarget(
                lease.playerUuid,
                target,
                key.centerX(),
                key.centerZ(),
                BLOCKS_PER_PIXEL
        );
        return true;
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
        lease.cached.renderer.removeTarget(lease.playerUuid);
    }

    private @Nullable World resolveWorld(String dimensionName) {
        NamespacedKey key = NamespacedKey.fromString(dimensionName);
        return key == null ? null : this.server.getWorld(key);
    }

    private static void configure(MapView view, World world, MapCacheKey key) {
        view.setWorld(world);
        view.setCenterX(key.centerX());
        view.setCenterZ(key.centerZ());
        view.setScale(key.scale());
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
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
        private final MapView view;
        private final PaperNavigationMapRenderer renderer;

        private CachedMap(
                MapView view,
                PaperNavigationMapRenderer renderer
        ) {
            this.view = view;
            this.renderer = renderer;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final PaperNavigationMapCache owner;
        private final CachedMap cached;
        private final UUID playerUuid;
        private boolean closed;

        private Lease(
                PaperNavigationMapCache owner,
                CachedMap cached,
                UUID playerUuid
        ) {
            this.owner = owner;
            this.cached = cached;
            this.playerUuid = playerUuid;
        }

        public MapView view() {
            this.assertOpen();
            return this.cached.view;
        }

        public boolean updateTarget(NavigationTarget target) {
            return this.owner.updateTarget(this, target);
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

}
