package _959.server_waypoint.common.client;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientWaypointSceneTest {
    @TempDir Path directory;

    @Test
    void unchangedSceneDoesNotRebuildMarkerPresentation() {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.markSnapshotApplied(ClientWaypointSource.SHARED);
        collections.files(ClientWaypointSource.SHARED).putWaypointList("minecraft:overworld",
                new WaypointList("Homes", 7, List.of(new SimpleWaypoint("Base", "B", new WaypointPos(1, 64, 1), 1, 0, false))));
        int[] calls = new int[3];
        var scene = new ClientWaypointScene(collections, new ClientWaypointScene.Markers() {
            public void add(SimpleWaypoint waypoint) { calls[0]++; }
            public void remove(SimpleWaypoint waypoint) { calls[1]++; }
            public void update(SimpleWaypoint waypoint) { calls[2]++; }
        });
        scene.refresh(ClientWaypointSource.SHARED, "minecraft:overworld");
        scene.refresh(ClientWaypointSource.SHARED, "minecraft:overworld");
        assertArrayEquals(new int[] {1, 0, 0}, calls);
    }

    @Test
    void replacementTombstoneVisibilityAndProfileChangesKeepOtherCollections() {
        var collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "test");
        collections.bindServer(directory, 1);
        collections.bindLocal(UUID.randomUUID(), new WaypointFilesManagerCore(directory.resolve("local")));
        var visible = Collections.newSetFromMap(new IdentityHashMap<SimpleWaypoint, Boolean>());
        var scene = new ClientWaypointScene(collections, new ClientWaypointScene.Markers() {
            public void add(SimpleWaypoint waypoint) { visible.add(waypoint); }
            public void remove(SimpleWaypoint waypoint) { visible.remove(waypoint); }
            public void update(SimpleWaypoint waypoint) { }
        });
        var originals = new EnumMap<ClientWaypointSource, SimpleWaypoint>(ClientWaypointSource.class);
        for (var source : ClientWaypointSource.values()) {
            var waypoint = new SimpleWaypoint("Base", "B", new WaypointPos(1, 64, 1), 0x123456, 0, source == ClientWaypointSource.PRIVATE);

            collections.markSnapshotApplied(source);
            collections.files(source).putWaypointList("minecraft:overworld", new WaypointList("Homes", "Homes", 7, List.of(waypoint)));
            originals.put(source, collections.files(source).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes").getWaypointByName("Base"));
            scene.refresh(source, "minecraft:overworld");
        }
        assertEquals(3, visible.size());
        assertFalse(originals.get(ClientWaypointSource.LOCAL).global());
        assertTrue(originals.get(ClientWaypointSource.PRIVATE).global());
        var hidden = collections.selection(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", "Base");
        scene.setVisible(hidden, false);
        assertEquals(2, visible.size());
        var replacement = new SimpleWaypoint("Base", "B", new WaypointPos(2, 64, 1), 0x123456, 0, false);
        collections.files(ClientWaypointSource.SHARED).putWaypointList("minecraft:overworld", new WaypointList("Homes", "Homes", 8, List.of(replacement)));
        replacement = collections.files(ClientWaypointSource.SHARED).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes").getWaypointByName("Base");
        scene.refresh(ClientWaypointSource.SHARED, "minecraft:overworld");
        assertEquals(Set.of(replacement, originals.get(ClientWaypointSource.PRIVATE)), visible);
        scene.setVisible(hidden, true);
        collections.files(ClientWaypointSource.PRIVATE).removeWaypointListImmediately("minecraft:overworld", "Homes");
        scene.refresh(ClientWaypointSource.PRIVATE, "minecraft:overworld");
        assertEquals(Set.of(replacement, originals.get(ClientWaypointSource.LOCAL)), visible);
        collections.bindLocal(UUID.randomUUID(), new WaypointFilesManagerCore(directory.resolve("other")));
        scene.refresh(ClientWaypointSource.LOCAL, "minecraft:overworld");
        assertEquals(Set.of(replacement), visible);
        assertFalse(collections.valid(hidden));
        assertEquals(1, collections.files(ClientWaypointSource.SHARED).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes").size());
    }
}
