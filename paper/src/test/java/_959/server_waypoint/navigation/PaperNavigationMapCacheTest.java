//? if >=26.2 {
package _959.server_waypoint.navigation;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.map.CraftMapCanvas;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaperNavigationMapCacheTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void movingToAnUnexploredRegionClearsOldTerrainAndInvalidatesTheWholeMap() throws Exception {
        Fixture fixture = fixture();
        MapItemSavedData data = fixture.data();
        Arrays.fill(data.colors, (byte) 12);
        Constructor<CraftMapCanvas> canvasConstructor = CraftMapCanvas.class.getDeclaredConstructor(data.mapView.getClass());
        canvasConstructor.setAccessible(true);
        CraftMapCanvas canvas = canvasConstructor.newInstance(data.mapView);
        data.mapView.getRenderers().get(0).render(data.mapView, canvas, null);
        assertEquals(12, canvas.getPixel(64, 64));

        MapItemSavedData.HoldingPlayer holder = data.getHoldingPlayer(null);
        field(holder.getClass(), "dirtyData").setBoolean(holder, false);
        data.setDirty(false);

        configure(data.mapView, fixture.world(), "minecraft:overworld", 4096, -4096);

        assertArrayEquals(new byte[128 * 128], data.colors);
        assertEquals(4096, data.centerX);
        assertEquals(-4096, data.centerZ);
        assertEquals(37, data.mapView.getId());
        assertTrue(data.isDirty());
        assertTrue(field(holder.getClass(), "dirtyData").getBoolean(holder));
        assertEquals(0, field(holder.getClass(), "minDirtyX").getInt(holder));
        assertEquals(0, field(holder.getClass(), "minDirtyY").getInt(holder));
        assertEquals(127, field(holder.getClass(), "maxDirtyX").getInt(holder));
        assertEquals(127, field(holder.getClass(), "maxDirtyY").getInt(holder));
        data.mapView.getRenderers().get(0).render(data.mapView, canvas, null);
        assertEquals(0, canvas.getPixel(64, 64));
    }

    @Test
    void changingDimensionAtTheSameCoordinatesClearsOldTerrain() throws Exception {
        Fixture fixture = fixture();
        fixture.data().dimension = Level.NETHER;
        Arrays.fill(fixture.data().colors, (byte) 12);

        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 0, 0);

        assertArrayEquals(new byte[128 * 128], fixture.data().colors);
        assertEquals(Level.OVERWORLD, fixture.data().dimension);
    }

    @Test
    void targetsInTheSameMapAreaKeepExploredTerrain() throws Exception {
        Fixture fixture = fixture();
        Arrays.fill(fixture.data().colors, (byte) 12);

        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 0, 0);

        byte[] expected = new byte[128 * 128];
        Arrays.fill(expected, (byte) 12);
        assertArrayEquals(expected, fixture.data().colors);
    }

    @Test
    void returningToAPreviouslyMappedRegionRestoresTerrainWithoutExploringAgain() throws Exception {
        Fixture fixture = fixture();
        Arrays.fill(fixture.data().colors, (byte) 12);
        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 4096, -4096);
        fixture.data().setColor(64, 64, (byte) 20);

        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 0, 0);

        byte[] expected = new byte[128 * 128];
        Arrays.fill(expected, (byte) 12);
        assertArrayEquals(expected, fixture.data().colors);
        assertEquals(37, fixture.data().mapView.getId());
    }

    @Test
    void terrainExploredAfterRetargetingSurvivesRepeatedConfiguration() throws Exception {
        Fixture fixture = fixture();
        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 4096, -4096);
        fixture.data().setColor(64, 64, (byte) 12);

        configure(fixture.data().mapView, fixture.world(), "minecraft:overworld", 4096, -4096);

        assertEquals(12, fixture.data().colors[64 + 64 * 128]);
    }

    private static Fixture fixture() throws Exception {
        MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 2, true, false, Level.OVERWORLD);
        data.id = new MapId(37);
        data.mapView.setCenterX(0);
        data.mapView.setCenterZ(0);
        SavedDataStorage storage = allocate(SavedDataStorage.class);
        field(SavedDataStorage.class, "cache").set(storage,
                new HashMap<>(java.util.Map.of(MapItemSavedData.type(data.id), Optional.of(data))));
        DedicatedServer server = allocate(DedicatedServer.class);
        field(MinecraftServer.class, "savedDataStorage").set(server, storage);
        ServerLevel level = allocate(ServerLevel.class);
        field(ServerLevel.class, "server").set(level, server);
        field(Level.class, "dimension").set(level, Level.OVERWORLD);
        field(ServerLevel.class, "uuid").set(level, new UUID(1, 2));
        CraftWorld world = allocate(CraftWorld.class);
        field(CraftWorld.class, "world").set(world, level);
        return new Fixture(data, world);
    }

    private void configure(MapView view, World world, String dimension, int x, int z) throws Exception {
        Class<?> keyType = Class.forName(PaperNavigationMapCache.class.getName() + "$MapCacheKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(
                String.class, int.class, int.class, MapView.Scale.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance(dimension, x, z, MapView.Scale.NORMAL);
        Method configure = PaperNavigationMapCache.class.getDeclaredMethod(
                "configure", MapView.class, World.class, keyType, NavigationMapTerrainStore.class);
        configure.setAccessible(true);
        configure.invoke(null, view, world, key, new NavigationMapTerrainStore(this.directory));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeType, "theUnsafe").get(null);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }

    private record Fixture(MapItemSavedData data, CraftWorld world) {
    }
}
//?}
