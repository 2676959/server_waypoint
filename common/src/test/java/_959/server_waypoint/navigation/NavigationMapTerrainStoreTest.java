package _959.server_waypoint.navigation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NavigationMapTerrainStoreTest {
    private static final NavigationMapTerrainStore.Region FIRST = region("minecraft:overworld", 0, 0, 2);
    private static final NavigationMapTerrainStore.Region SECOND = region("minecraft:overworld", 4096, -4096, 2);
    @TempDir
    Path directory;

    @Test
    void returningToARegionRestoresItsOwnPixelsAndKeepsNewRegionsBlank() {
        NavigationMapTerrainStore store = new NavigationMapTerrainStore(this.directory);
        byte[] first = pixels(12);
        byte[] second = store.switchRegion(37, FIRST, first, SECOND);
        assertArrayEquals(new byte[128 * 128], second);
        second[64 + 64 * 128] = 20;

        assertArrayEquals(first, store.switchRegion(37, SECOND, second, FIRST));
        assertArrayEquals(second, store.switchRegion(37, FIRST, first, SECOND));
    }

    @Test
    void historySurvivesReopeningTheStoreAndDoesNotShareMutableArrays() {
        NavigationMapTerrainStore store = new NavigationMapTerrainStore(this.directory);
        byte[] colors = pixels(12);
        store.switchRegion(37, FIRST, colors, SECOND);
        Arrays.fill(colors, (byte) 20);

        NavigationMapTerrainStore reopened = new NavigationMapTerrainStore(this.directory);
        assertArrayEquals(pixels(12), reopened.restore(37, FIRST));
        byte[] restored = reopened.restore(37, FIRST);
        Arrays.fill(restored, (byte) 24);
        assertArrayEquals(pixels(12), reopened.restore(37, FIRST));
    }

    @Test
    void mapsDimensionsCentersAndScalesHaveSeparateTerrain() {
        NavigationMapTerrainStore store = new NavigationMapTerrainStore(this.directory);
        store.switchRegion(37, FIRST, pixels(12), SECOND);

        assertArrayEquals(new byte[128 * 128], store.restore(38, FIRST));
        assertArrayEquals(new byte[128 * 128], store.restore(37, region("minecraft:the_nether", 0, 0, 2)));
        assertArrayEquals(new byte[128 * 128], store.restore(37, region("minecraft:overworld", 512, 0, 2)));
        assertArrayEquals(new byte[128 * 128], store.restore(37, region("minecraft:overworld", 0, 512, 2)));
        assertArrayEquals(new byte[128 * 128], store.restore(37, region("minecraft:overworld", 0, 0, 1)));
        assertArrayEquals(pixels(12), store.restore(37, FIRST));
    }

    @Test
    void updatingWithinTheSameRegionKeepsLivePixelsWithoutWritingAFile() throws Exception {
        NavigationMapTerrainStore store = new NavigationMapTerrainStore(this.directory);
        byte[] colors = pixels(12);

        assertSame(colors, store.switchRegion(37, FIRST, colors, FIRST));
        try (var files = Files.list(this.directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void laterExplorationReplacesThePreviouslySavedSnapshot() {
        NavigationMapTerrainStore store = new NavigationMapTerrainStore(this.directory);
        byte[] colors = pixels(12);
        store.switchRegion(37, FIRST, colors, SECOND);
        colors[0] = 20;
        store.switchRegion(37, FIRST, colors, SECOND);

        assertArrayEquals(colors, store.restore(37, FIRST));
    }

    private static NavigationMapTerrainStore.Region region(String dimension, int x, int z, int scale) {
        return new NavigationMapTerrainStore.Region(dimension, x, z, scale);
    }

    private static byte[] pixels(int color) {
        byte[] colors = new byte[128 * 128];
        Arrays.fill(colors, (byte) color);
        return colors;
    }
}
