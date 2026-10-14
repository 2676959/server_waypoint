package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.integrations.ClientWaypointSyncEvent;
import _959.server_waypoint.common.client.integrations.MapModIntegration;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.upload.UploadTarget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import _959.server_waypoint.common.server.LocalWaypointUpload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientManualDownloadTest {
    private static final String DIMENSION = "minecraft:overworld";
    private final Map<Field, Object> previousValues = new LinkedHashMap<>();
    @TempDir Path directory;
    private WaypointClientMod client;
    private Unsafe unsafe;
    private Object integrationsBase;
    private long integrationsOffset;
    private Object previousIntegrations;
    private RecordingIntegration xaero;
    private RecordingIntegration voxelMap;

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
        setStatic(WaypointClientMod.class, "currentDimensionName", "minecraft:the_nether");
        setStatic(WaypointClientMod.class, "isXaerosMinimapReady", true);
        setStatic(WaypointServerMod.class, "runsWithClient", false);
        setStatic(ClientConfig.class, "isXaerosMinimapLoaded", false);
        setStatic(ClientConfig.class, "isVoxelMapLoaded", false);
        setStatic(LocalWaypointUpload.class, "handler", null);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);
        Field integrations = MapModIntegrations.class.getDeclaredField("INTEGRATIONS");
        integrations.setAccessible(true);
        previousIntegrations = integrations.get(null);
        integrationsBase = unsafe.staticFieldBase(integrations);
        integrationsOffset = unsafe.staticFieldOffset(integrations);
        xaero = new RecordingIntegration(UploadTarget.XAERO);
        voxelMap = new RecordingIntegration(UploadTarget.VOXELMAP);
        unsafe.putObject(integrationsBase, integrationsOffset, List.of(xaero, voxelMap));
        config.setAutoSyncToXaerosMinimap(false);
        config.setAutoSyncToVoxelMap(false);
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
        client = constructor.newInstance(minecraft, directory, directory);
        client.changeWaypointFilesDir(directory.resolve("cached"));
        client.putWaypointList(DIMENSION, WaypointList.build("cached", 7));
    }

    @AfterEach
    void restoreState() throws IllegalAccessException {
        unsafe.putObject(integrationsBase, integrationsOffset, previousIntegrations);
        for (var entry : previousValues.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void listDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() {
        receive(WaypointData.waypointList(DIMENSION, WaypointList.build("cached", 7)));

        assertRefreshed(ClientWaypointSyncEvent.Type.LIST_REPLACED);
    }

    @Test
    void dimensionDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() {
        receive(WaypointData.dimension(new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 7)))));

        assertRefreshed(ClientWaypointSyncEvent.Type.DIMENSION_REPLACED);
    }

    @Test
    void worldDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() throws Exception {
        prepareWorld();
        receive(WaypointData.world(List.of(new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 7))))));

        assertRefreshed(ClientWaypointSyncEvent.Type.WORLD_REPLACED);
    }

    @Test
    void integratedListDownloadRefreshesBothMapsWithoutReplacingSharedStorage() throws Exception {
        setStatic(WaypointServerMod.class, "runsWithClient", true);
        WaypointList shared = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached");
        receive(WaypointData.waypointList(DIMENSION, WaypointList.build("cached", 7)));

        assertRefreshed(ClientWaypointSyncEvent.Type.LIST_REPLACED);
        assertSame(shared, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached"));
    }

    @Test
    void singleWaypointDownloadRepairsCachedWaypointAtTheSameRevision() {
        SimpleWaypoint damaged = waypoint("spawn", 99);
        client.putWaypointList(DIMENSION, new WaypointList("cached", 7,
                List.of(damaged, waypoint("personal", 25))));
        WaypointList snapshot = new WaypointList("cached", 7, List.of(waypoint("spawn", 10)));
        receive(WaypointData.waypoint(DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

        assertRefreshed(ClientWaypointSyncEvent.Type.WAYPOINT_ADDED);
        WaypointList repaired = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached");
        assertEquals(10, repaired.getWaypointByName("spawn").x());
        assertEquals(25, repaired.getWaypointByName("personal").x());
        assertEquals(2, repaired.size());
        assertEquals(7, repaired.getSyncNum());
        assertEquals("spawn", xaero.lastEvent.waypoint().name());
        assertEquals("spawn", voxelMap.lastEvent.waypoint().name());
    }

    @Test
    void downloadingIntoAMissingListLeavesItsRevisionUnknown() {
        WaypointList snapshot = new WaypointList("missing", 12, List.of(waypoint("spawn", 10)));
        receive(WaypointData.waypoint(DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

        assertRefreshed(ClientWaypointSyncEvent.Type.WAYPOINT_ADDED);
        WaypointList repaired = client.getWaypointFileManager(DIMENSION).getWaypointListByName("missing");
        assertEquals(1, repaired.size());
        assertEquals(0, repaired.getSyncNum());
    }

    @Test
    void singleWaypointDownloadBypassesOutOfSyncStateWithoutCertifyingTheWholeList() throws Exception {
        Field trackerField = WaypointClientMod.class.getDeclaredField("synchronizationTracker");
        trackerField.setAccessible(true);
        ClientSynchronizationTracker tracker = (ClientSynchronizationTracker) trackerField.get(client);
        tracker.markOutOfSync(DIMENSION, "cached");
        WaypointList snapshot = new WaypointList("cached", 12, List.of(waypoint("spawn", 10)));
        receive(WaypointData.waypoint(DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

        assertRefreshed(ClientWaypointSyncEvent.Type.WAYPOINT_ADDED);
        assertEquals(10, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached")
                .getWaypointByName("spawn").x());
        assertEquals(7, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getSyncNum());
        org.junit.jupiter.api.Assertions.assertTrue(tracker.isOutOfSync(DIMENSION, "cached"));
    }

    @Test
    void integratedWorldDownloadRefreshesBothMapsWithoutReplacingSharedStorage() throws Exception {
        setStatic(WaypointServerMod.class, "runsWithClient", true);
        WaypointList shared = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached");
        receive(WaypointData.world(List.of(new DimensionWaypointData(DIMENSION, List.of(shared)))));

        assertRefreshed(ClientWaypointSyncEvent.Type.WORLD_REPLACED);
        assertSame(shared, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached"));
    }

    private void prepareWorld() throws Exception {
        // Only Fabric can create a client world without launching the mod loader.
        //? if fabric {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ClientLevel level = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
        Field dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        dimension.set(level, Level.OVERWORLD);
        Minecraft.getInstance().level = level;
        //?} else {
        /*org.junit.jupiter.api.Assumptions.abort("Client world creation requires the Forge-family loader");
        *///?}
    }

    @Test
    void manualDownloadSkipsAbsentAndUnreadyMaps() {
        xaero.installed = false;
        voxelMap.ready = false;
        receive(WaypointData.waypointList(DIMENSION, WaypointList.build("cached", 7)));

        assertNull(xaero.lastEvent);
        assertNull(voxelMap.lastEvent);
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(name, "S", new WaypointPos(x, 64, 0), 0xFFFFFF, 0, false);
    }

    @Test
    void automaticUpdatesStillRespectDisabledAutoSync() {
        receive(WaypointData.updates(List.of(new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 8))))));

        assertNull(xaero.lastEvent);
        assertNull(voxelMap.lastEvent);
        assertEquals(8, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getSyncNum());
    }

    private void assertRefreshed(ClientWaypointSyncEvent.Type type) {
        assertNotNull(xaero.lastEvent);
        assertNotNull(voxelMap.lastEvent);
        assertEquals(type, xaero.lastEvent.type());
        assertEquals(type, voxelMap.lastEvent.type());
    }

    private void receive(WaypointData data) {
        for (var frame : ChunkedMessageManager.createTransfer(data, false)) {
            client.onMessageChunk(frame);
        }
    }

    // Substitute only the external map storage boundary; command data, transport,
    // cache updates and integration dispatch remain real.
    private static final class RecordingIntegration implements MapModIntegration {
        private final UploadTarget target;
        private ClientWaypointSyncEvent lastEvent;
        private boolean installed = true;
        private boolean ready = true;

        private RecordingIntegration(UploadTarget target) {
            this.target = target;
        }

        @Override public UploadTarget uploadTarget() { return target; }
        @Override public WaypointData collectUpload(UploadRequestBuffer request) { throw new UnsupportedOperationException(); }
        @Override public boolean isEnabled(ClientConfig config) {
            return target == UploadTarget.XAERO
                    ? config.isAutoSyncToXaerosMinimap() : config.isAutoSyncToVoxelMap();
        }
        @Override public boolean isInstalled() { return installed; }
        @Override public boolean isReady() { return ready; }
        @Override public void syncAll(WaypointClientMod client) { throw new UnsupportedOperationException(); }
        @Override public void onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod client) {
            lastEvent = event;
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
