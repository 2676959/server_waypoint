package _959.server_waypoint.common.client;

import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteClientDispatchTest {
    @TempDir Path directory;
    @Test void fragmentedRemoteReplacementCannotChangeLocalManagersOrFiles() throws Exception {
        var constructor = WaypointClientMod.class.getDeclaredConstructor(Minecraft.class, Path.class, Path.class);
        constructor.setAccessible(true);
        var instance = WaypointClientMod.class.getDeclaredField("INSTANCE"); instance.setAccessible(true);
        var network = WaypointClientMod.class.getDeclaredField("networkState"); network.setAccessible(true);
        Object previous = instance.get(null), previousNetwork = network.get(null);
        try {
            var client = constructor.newInstance(null, directory, directory);
            client.changeWaypointFilesDir(directory.resolve("local"));
            client.putWaypointList("minecraft:overworld", WaypointList.build("local-marker", 7));
            client.saveAllWaypointFiles();
            var local = client.getWaypointFileManager("minecraft:overworld");
            Map<Path, String> disk = files(directory);
            network.set(null, WaypointClientMod.ClientNetworkState.SYNC_FINISHED);
            client.remoteCatalogs().clear();
            var request = client.remoteCatalogs().poll();
            var id = new RemoteServerId("elsewhere");
            var waypoint = new RemoteWaypointSnapshot("local-marker", "R", new WaypointPos(1, 2, 3), 0, 0, false,
                    List.of("k".repeat(60000)), "d".repeat(60000));
            var snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(9), Map.of("minecraft:overworld",
                    Map.of("local-marker", new RemoteListSnapshot("local-marker", new RemoteRevision(8), Map.of("Exact Name", waypoint)))), Instant.EPOCH);
            var message = new RemoteCatalogMessage(request.requestId(), RemoteCatalogState.AVAILABLE,
                    Map.of(id, new CatalogReceiver.View(snapshot, RemoteCatalogState.AVAILABLE, "same name", null, "minecraft:compass")));
            var frames = ChunkedMessageManager.createTransfer(message, false);
            assertTrue(frames.size() > 1);
            for (int i = 0; i < frames.size() - 1; i++) client.onMessageChunk(frames.get(i));
            assertTrue(client.remoteCatalogs().snapshot().isEmpty());
            client.onMessageChunk(frames.get(frames.size() - 1));
            assertEquals(snapshot.dimensions(), client.remoteCatalogs().snapshot().get(id).snapshot().dimensions());
            assertSame(local, client.getWaypointFileManager("minecraft:overworld"));
            assertEquals(Set.of("local-marker"), local.getWaypointListMap().keySet());
            assertEquals(7, local.getWaypointListMap().get("local-marker").getSyncNum());
            assertEquals(disk, files(directory));
            client.remoteCatalogs().clear();
            network.set(null, WaypointClientMod.ClientNetworkState.INCOMPATIBLE_PROTOCOL);
            for (var frame : ChunkedMessageManager.createTransfer(message, false)) client.onMessageChunk(frame);
            assertTrue(client.remoteCatalogs().snapshot().isEmpty());
            assertEquals(disk, files(directory));
        } finally { instance.set(null, previous); network.set(null, previousNetwork); }
    }
    private static Map<Path, String> files(Path root) throws Exception {
        Map<Path, String> contents = new HashMap<>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) contents.put(path, Files.readString(path));
        }
        return contents;
    }
}
