package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.server.LocalWaypointUpload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientHandshakeMapSyncTest {
    private static final String DIMENSION = "minecraft:overworld";
    private final Map<Field, Object> previousValues = new LinkedHashMap<>();
    @TempDir Path directory;
    private WaypointClientMod client;

    @BeforeEach
    void awaitInitialUpdates() throws ReflectiveOperationException {
        var configConstructor = ClientConfig.class.getDeclaredConstructor();
        configConstructor.setAccessible(true);
        var config = configConstructor.newInstance();
        config.setAutoSyncToXaerosMinimap(true);
        config.setAutoSyncToVoxelMap(true);
        setStatic(WaypointClientMod.class, "clientConfig", config);
        setStatic(WaypointClientMod.class, "INSTANCE", null);
        setStatic(WaypointClientMod.class, "networkState", WaypointClientMod.ClientNetworkState.HANDSHAKE_FINISHED);
        setStatic(WaypointClientMod.class, "currentDimensionName", DIMENSION);
        setStatic(WaypointClientMod.class, "isXaerosMinimapReady", true);
        setStatic(WaypointServerMod.class, "runsWithClient", false);
        setStatic(ClientConfig.class, "isXaerosMinimapLoaded", false);
        setStatic(ClientConfig.class, "isVoxelMapLoaded", false);
        setStatic(LocalWaypointUpload.class, "handler", null);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(minecraft, new TestFont());
        Field renderer = Minecraft.class.getDeclaredField("gameRenderer");
        renderer.setAccessible(true);
        renderer.set(minecraft, unsafe.allocateInstance(GameRenderer.class));
        setStatic(Minecraft.class, "instance", minecraft);
        var constructor = WaypointClientMod.class.getDeclaredConstructor(Minecraft.class, Path.class, Path.class);
        constructor.setAccessible(true);
        client = constructor.newInstance(null, directory, directory);
        client.changeWaypointFilesDir(directory.resolve("cached"));
        client.putWaypointList(DIMENSION, WaypointList.build("cached", 7));
    }

    @AfterEach
    void restoreState() throws IllegalAccessException {
        for (var entry : previousValues.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void unchangedHandshakeCompletesWithoutAccessingXaeroStorage() throws ReflectiveOperationException {
        setStatic(ClientConfig.class, "isXaerosMinimapLoaded", true);
        assertUnchangedHandshakeCompletes();
    }

    //? if voxelmap {
    @Test
    void unchangedHandshakeCompletesWithoutAccessingVoxelMapStorage() throws ReflectiveOperationException {
        setStatic(ClientConfig.class, "isVoxelMapLoaded", true);
        assertUnchangedHandshakeCompletes();
    }
    //?}

    @Test
    void initialUpdatesStillReplaceCachedLists() {
        receiveUpdates(List.of(new DimensionWaypointData(DIMENSION, List.of(WaypointList.build("cached", 8)))));

        assertEquals(8, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getSyncNum());
        assertEquals(WaypointClientMod.ClientNetworkState.SYNC_FINISHED, WaypointClientMod.getNetworkState());
    }

    @Test
    void initialUpdatesStillRemoveDeletedDimensions() {
        receiveUpdates(List.of(new DimensionWaypointData(DIMENSION, List.of())));

        assertNull(client.getWaypointFileManager(DIMENSION));
        assertEquals(WaypointClientMod.ClientNetworkState.SYNC_FINISHED, WaypointClientMod.getNetworkState());
    }

    private void assertUnchangedHandshakeCompletes() {
        var cachedManager = client.getWaypointFileManager(DIMENSION);
        var cachedList = cachedManager.getWaypointListByName("cached");
        // No map-mod session exists here: unchanged updates must finish without touching
        // their storage, even though automatic map sync is enabled.
        receiveUpdates(List.of());

        assertEquals(WaypointClientMod.ClientNetworkState.SYNC_FINISHED, WaypointClientMod.getNetworkState());
        assertSame(cachedManager, client.getWaypointFileManager(DIMENSION));
        assertSame(cachedList, client.getCurrentWaypointLists().get(0));
        assertEquals(7, cachedList.getSyncNum());
    }

    private void receiveUpdates(List<DimensionWaypointData> updates) {
        for (var frame : ChunkedMessageManager.createTransfer(WaypointData.updates(updates), false)) {
            client.onMessageChunk(frame);
        }
    }

    private void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        if (!previousValues.containsKey(field)) {
            previousValues.put(field, field.get(null));
        }
        field.set(null, value);
    }
}
