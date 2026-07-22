package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.navigation.NavigationTarget;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

//? if >= 1.20.5 {
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
//?} else {
/*import net.minecraft.world.level.saveddata.maps.MapDecoration;
*///?}

/**
 * Reuses navigation map IDs without ever recycling an ID leased by an active
 * session. The retained ID count is bounded by peak concurrent map targets and
 * retarget transitions rather than by the number of historical target edits.
 */
final class ModNavigationMapCache {
    private static final byte NAVIGATION_SCALE = 2;
    private static final String TARGET_MARKER_ID = "server_waypoint:navigation_target";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PERSISTED_CACHE_TYPE = new TypeToken<Map<String, Integer>>() {
    }.getType();

    private final NavigationMapLeasePool<MapKey> leases = new NavigationMapLeasePool<>();
    private MinecraftServer loadedServer;
    private Path cachePath;

    @Nullable PreparedMap prepare(ServerLevel targetLevel, NavigationTarget target) {
        this.ensureLoaded(targetLevel.getServer());
        MapKey key = MapKey.from(target);
        Integer existingId = this.leases.idFor(key);
        if (existingId != null) {
            ItemStack recovered = recoverMap(targetLevel, target, key, existingId);
            if (recovered != null) {
                return new PreparedMap(key, recovered);
            }
            if (this.leases.isActive(key)) {
                return null;
            }
            this.leases.forget(key);
        }

        NavigationMapLeasePool.ReusableEntry<MapKey> reusable = this.leases.removeOldestInactive();
        ItemStack map;
        int mapId;
        if (reusable == null) {
            map = createMap(targetLevel, target);
            mapId = mapId(map);
        } else {
            mapId = reusable.mapId();
            replaceMapData(targetLevel, target, mapId);
            map = mapStack(mapId, target);
        }
        this.leases.remember(key, mapId);
        this.save();
        return new PreparedMap(key, map);
    }

    void activate(UUID playerUuid, PreparedMap preparedMap) {
        this.leases.activate(playerUuid, preparedMap.key);
    }

    void release(UUID playerUuid) {
        this.leases.release(playerUuid);
    }

    void clear() {
        this.leases.clear();
        this.loadedServer = null;
        this.cachePath = null;
    }

    private static ItemStack createMap(ServerLevel targetLevel, NavigationTarget target) {
        ItemStack map = MapItem.create(
                targetLevel,
                target.position().x(),
                target.position().z(),
                NAVIGATION_SCALE,
                true,
                false
        );
        addMarker(map, target);
        return map;
    }

    private static void replaceMapData(ServerLevel targetLevel, NavigationTarget target, int mapId) {
        MapItemSavedData savedData = MapItemSavedData.createFresh(
                target.position().x(),
                target.position().z(),
                NAVIGATION_SCALE,
                true,
                false,
                targetLevel.dimension()
        );
        //? if >= 1.20.5 {
        targetLevel.setMapData(new MapId(mapId), savedData);
        //?} else {
        /*targetLevel.setMapData(MapItem.makeKey(mapId), savedData);
        *///?}
    }

    private static @Nullable ItemStack recoverMap(
            ServerLevel targetLevel,
            NavigationTarget target,
            MapKey key,
            int mapId
    ) {
        ItemStack map = mapStack(mapId, target);
        MapItemSavedData savedData = MapItem.getSavedData(map, targetLevel);
        if (savedData == null
                || savedData.scale != key.scale()
                || savedData.centerX != key.centerX()
                || savedData.centerZ != key.centerZ()
                || !savedData.dimension.equals(targetLevel.dimension())) {
            return null;
        }
        return map;
    }

    private static ItemStack mapStack(int mapId, NavigationTarget target) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        //? if >= 1.20.5 {
        map.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(mapId));
        //?} else {
        /*map.getOrCreateTag().putInt("map", mapId);
        *///?}
        addMarker(map, target);
        return map;
    }

    private static void addMarker(ItemStack map, NavigationTarget target) {
        BlockPos position = new BlockPos(
                target.position().x(),
                target.position().y(),
                target.position().z()
        );
        //? if >= 1.20.5 {
        MapItemSavedData.addTargetDecoration(map, position, TARGET_MARKER_ID, MapDecorationTypes.TARGET_X);
        //?} else {
        /*MapItemSavedData.addTargetDecoration(map, position, TARGET_MARKER_ID, MapDecoration.Type.TARGET_X);
        *///?}
    }

    private static int mapId(ItemStack map) {
        //? if >= 1.20.5 {
        MapId id = map.get(net.minecraft.core.component.DataComponents.MAP_ID);
        if (id == null) {
            throw new IllegalStateException("Generated navigation map has no map ID");
        }
        return id.id();
        //?} else {
        /*return map.getOrCreateTag().getInt("map");
        *///?}
    }

    private void ensureLoaded(MinecraftServer server) {
        if (this.loadedServer == server) {
            return;
        }
        this.leases.clear();
        this.loadedServer = server;
        this.cachePath = server.getWorldPath(LevelResource.ROOT)
                .resolve("server_waypoint")
                .resolve("navigation-map-cache.json");
        if (!Files.isRegularFile(this.cachePath)) {
            return;
        }
        try (java.io.Reader reader = Files.newBufferedReader(this.cachePath, StandardCharsets.UTF_8)) {
            Map<String, Integer> loaded = GSON.fromJson(reader, PERSISTED_CACHE_TYPE);
            if (loaded == null) {
                return;
            }
            for (Map.Entry<String, Integer> entry : loaded.entrySet()) {
                MapKey.parse(entry.getKey()).ifPresent(key -> this.leases.remember(key, entry.getValue()));
            }
        } catch (IOException | JsonParseException exception) {
            WaypointServerMod.LOGGER.warn("Could not read navigation map cache {}", this.cachePath, exception);
        }
    }

    private void save() {
        if (this.cachePath == null) {
            return;
        }
        Map<String, Integer> persisted = new LinkedHashMap<>();
        for (Map.Entry<MapKey, Integer> entry : this.leases.entries().entrySet()) {
            persisted.put(entry.getKey().persistenceKey(), entry.getValue());
        }
        Path temporaryPath = this.cachePath.resolveSibling(this.cachePath.getFileName() + ".tmp");
        try {
            Files.createDirectories(this.cachePath.getParent());
            try (java.io.Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8)) {
                GSON.toJson(persisted, PERSISTED_CACHE_TYPE, writer);
            }
            try {
                Files.move(
                        temporaryPath,
                        this.cachePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, this.cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            WaypointServerMod.LOGGER.warn("Could not save navigation map cache {}", this.cachePath, exception);
        }
    }

    static final class PreparedMap {
        private final MapKey key;
        private final ItemStack item;

        private PreparedMap(MapKey key, ItemStack item) {
            this.key = key;
            this.item = item;
        }

        ItemStack item() {
            return this.item;
        }
    }

    private record MapKey(
            String dimension,
            int centerX,
            int centerZ,
            byte scale,
            int targetX,
            int targetZ
    ) {
        private static MapKey from(NavigationTarget target) {
            return new MapKey(
                    target.dimensionName(),
                    mapCenter(target.position().x()),
                    mapCenter(target.position().z()),
                    NAVIGATION_SCALE,
                    target.position().x(),
                    target.position().z()
            );
        }

        private static java.util.Optional<MapKey> parse(String persistedKey) {
            String[] parts = persistedKey.split("\\.", -1);
            if (parts.length != 7 || !"v2".equals(parts[0])) {
                return java.util.Optional.empty();
            }
            try {
                String dimension = new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        StandardCharsets.UTF_8
                );
                return java.util.Optional.of(new MapKey(
                        dimension,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        Byte.parseByte(parts[4]),
                        Integer.parseInt(parts[5]),
                        Integer.parseInt(parts[6])
                ));
            } catch (IllegalArgumentException exception) {
                return java.util.Optional.empty();
            }
        }

        private String persistenceKey() {
            String encodedDimension = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(this.dimension.getBytes(StandardCharsets.UTF_8));
            return "v2." + encodedDimension
                    + "." + this.centerX
                    + "." + this.centerZ
                    + "." + this.scale
                    + "." + this.targetX
                    + "." + this.targetZ;
        }
    }

    private static int mapCenter(int coordinate) {
        int mapSize = 128 * (1 << NAVIGATION_SCALE);
        return Math.floorDiv(coordinate + 64, mapSize) * mapSize + mapSize / 2 - 64;
    }
}
