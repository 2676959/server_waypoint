package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.navigation.NavigationMapTerrainStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModNavigationMapCacheTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        //? if fabric {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        //?} else {
        /*try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable unavailableRuntime) {
            org.junit.jupiter.api.Assumptions.abort("Minecraft bootstrap unavailable: " + unavailableRuntime);
        }
        _959.server_waypoint.common.MinecraftTestRuntime.assumeEntityTypesAreRegistered();
        *///?}
    }

    @Test
    void changingTargetWithinTheMapAreaPreservesExploredTerrain() {
        MapItemSavedData current = exploredMap();

        MapItemSavedData selected = this.select(current, Level.OVERWORLD, target(100, 200));

        assertSame(current, selected);
        assertEquals(12, selected.colors[64 + 64 * 128]);
    }

    @Test
    void mapRegionEdgesPreserveTerrainUntilTheTargetCrossesThem() {
        MapItemSavedData current = exploredMap();
        // Scale two maps created at (0, 0) cover [-64, 448) on each axis.
        assertSame(current, this.select(current, Level.OVERWORLD, target(-64, 447)));
        assertSame(current, this.select(current, Level.OVERWORLD, target(447, -64)));
        assertNotSame(current, this.select(current, Level.OVERWORLD, target(448, 0)));
        assertNotSame(current, this.select(current, Level.OVERWORLD, target(0, -65)));
    }

    @Test
    void changingToANewRegionCreatesBlankTerrainAtTheNewCenter() {
        MapItemSavedData current = exploredMap();

        MapItemSavedData selected = this.select(current, Level.OVERWORLD, target(4096, -4096));

        assertNotSame(current, selected);
        assertArrayEquals(new byte[128 * 128], selected.colors);
        assertEquals(4288, selected.centerX);
        assertEquals(-3904, selected.centerZ);
        assertEquals(12, current.colors[0]);

        // The same map ID must overwrite terrain already cached by the client.
        MapItemSavedData client = MapItemSavedData.createForClient((byte) 2, false, Level.OVERWORLD);
        Arrays.fill(client.colors, (byte) 12);
        selected.getHoldingPlayer(null);
        //? if >=1.20.5 {
        ClientboundMapItemDataPacket packet = (ClientboundMapItemDataPacket) selected.getUpdatePacket(
                new net.minecraft.world.level.saveddata.maps.MapId(37), null);
        //?} else {
        /*ClientboundMapItemDataPacket packet = (ClientboundMapItemDataPacket) selected.getUpdatePacket(37, null);
        *///?}
        assertNotNull(packet);
        packet.applyToMap(client);
        assertArrayEquals(new byte[128 * 128], client.colors);
    }

    @Test
    void changingDimensionAtTheSameCoordinatesCreatesBlankTerrain() {
        MapItemSavedData current = exploredMap();

        MapItemSavedData selected = this.select(current, Level.NETHER, target(0, 0));

        assertNotSame(current, selected);
        assertEquals(Level.NETHER, selected.dimension);
        assertArrayEquals(new byte[128 * 128], selected.colors);
    }

    @Test
    void repeatedTargetUpdatesKeepTerrainExploredInTheNewRegion() {
        MapItemSavedData selected = this.select(exploredMap(), Level.OVERWORLD, target(4096, -4096));
        selected.setColor(64, 64, (byte) 12);

        MapItemSavedData repeated = this.select(selected, Level.OVERWORLD, target(4200, -4000));

        assertSame(selected, repeated);
        assertEquals(12, repeated.colors[64 + 64 * 128]);
    }

    @Test
    void returningToAPreviouslyMappedRegionRestoresTerrainWithoutExploringAgain() {
        MapItemSavedData first = exploredMap();
        MapItemSavedData second = this.select(first, Level.OVERWORLD, target(4096, -4096));
        second.setColor(64, 64, (byte) 20);

        MapItemSavedData restored = this.select(second, Level.OVERWORLD, target(0, 0));

        byte[] expected = new byte[128 * 128];
        Arrays.fill(expected, (byte) 12);
        assertArrayEquals(expected, restored.colors);
    }

    @Test
    void missingSavedDataCreatesANavigationMap() {
        MapItemSavedData selected = this.select(null, Level.OVERWORLD, target(0, 0));

        assertEquals(192, selected.centerX);
        assertEquals(192, selected.centerZ);
        assertEquals(2, selected.scale);
        assertArrayEquals(new byte[128 * 128], selected.colors);
    }

    private MapItemSavedData select(MapItemSavedData current, ResourceKey<Level> dimension, NavigationTarget target) {
        return ModNavigationMapCache.mapDataForTarget(
                current, dimension, target, new NavigationMapTerrainStore(this.directory), 37);
    }

    private static MapItemSavedData exploredMap() {
        MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 2, true, false, Level.OVERWORLD);
        Arrays.fill(map.colors, (byte) 12);
        return map;
    }

    private static NavigationTarget target(int x, int z) {
        return new NavigationTarget("minecraft:overworld", "places", "Places", "target", "Target", "",
                List.of(), new WaypointPos(x, 64, z), 0x447FFF);
    }
}
