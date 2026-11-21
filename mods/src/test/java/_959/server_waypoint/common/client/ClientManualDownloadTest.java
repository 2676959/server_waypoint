package _959.server_waypoint.common.client;

import _959.server_waypoint.core.collection.WaypointScope;
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
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.WaypointCollectionStateMessage;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static _959.server_waypoint.core.waypoint.WaypointModificationType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientManualDownloadTest {
    private static final String DIMENSION = "minecraft:overworld";
    private final Map<Field, Object> previousValues = new LinkedHashMap<>();
    private final List<Map.Entry<WaypointModificationType, String>> updateNotifications = new ArrayList<>();
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
        client = new WaypointClientMod(minecraft, directory, directory,
                (update, waypoint) -> updateNotifications.add(Map.entry(update.type(), waypoint.displayName())));
        client.changeWaypointFilesDir(directory.resolve("cached"));
        client.putWaypointList(DIMENSION, WaypointList.build("cached", 7));
    }

    @AfterEach
    void restoreState() throws IllegalAccessException {
        _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.clearScene();
        unsafe.putObject(integrationsBase, integrationsOffset, previousIntegrations);
        for (var entry : previousValues.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void listDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() {
        receive(WaypointData.waypointList(WaypointScope.SHARED, DIMENSION, WaypointList.build("cached", 7)));

        assertRefreshed(ClientWaypointSyncEvent.Type.LIST_REPLACED);
    }

    @Test
    void dimensionDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() {
        receive(WaypointData.dimension(WaypointScope.SHARED, new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 7)))));

        assertRefreshed(ClientWaypointSyncEvent.Type.DIMENSION_REPLACED);
    }

    @Test
    void worldDownloadRefreshesBothMapsWithAutoSyncDisabledAndUnchangedRevision() throws Exception {
        prepareWorld();
        receive(WaypointData.world(WaypointScope.SHARED, List.of(new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 7))))));

        assertRefreshed(ClientWaypointSyncEvent.Type.WORLD_REPLACED);
    }

    @Test
    void integratedListDownloadRefreshesBothMapsWithoutReplacingSharedStorage() throws Exception {
        setStatic(WaypointServerMod.class, "runsWithClient", true);
        WaypointList shared = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached");
        receive(WaypointData.waypointList(WaypointScope.SHARED, DIMENSION, WaypointList.build("cached", 7)));

        assertRefreshed(ClientWaypointSyncEvent.Type.LIST_REPLACED);
        assertSame(shared, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached"));
    }

    @Test
    void singleWaypointDownloadRepairsCachedWaypointAtTheSameRevision() {
        SimpleWaypoint damaged = waypoint("spawn", 99);
        client.putWaypointList(DIMENSION, new WaypointList("cached", 7,
                List.of(damaged, waypoint("personal", 25))));
        WaypointList snapshot = new WaypointList("cached", 7, List.of(waypoint("spawn", 10)));
        receive(WaypointData.waypoint(WaypointScope.SHARED, DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

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
        receive(WaypointData.waypoint(WaypointScope.SHARED, DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

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
        receive(WaypointData.waypoint(WaypointScope.SHARED, DIMENSION, snapshot, snapshot.simpleWaypoints().get(0)));

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
        receive(WaypointData.world(WaypointScope.SHARED, List.of(new DimensionWaypointData(DIMENSION, List.of(shared)))));

        assertRefreshed(ClientWaypointSyncEvent.Type.WORLD_REPLACED);
        assertSame(shared, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached"));
    }

    @Test
    void sharedDownloadKeepsHiddenMarkerHiddenAndListRemovalReleasesOnlyShared() throws Exception {
        prepareWorld();
        setStatic(WaypointClientMod.class, "currentDimensionName", DIMENSION);
        setStatic(WaypointClientMod.class, "networkState", WaypointClientMod.ClientNetworkState.SYNC_FINISHED);
        client.collections().markSnapshotApplied(ClientWaypointSource.SHARED);
        client.collections().bindLocal(java.util.UUID.randomUUID(), new _959.server_waypoint.core.WaypointFilesManagerCore(directory.resolve("local")));
        client.putWaypointList(DIMENSION, new WaypointList("cached", 7, List.of(waypoint("spawn", 10))));
        client.collections().files(ClientWaypointSource.LOCAL).putWaypointList(DIMENSION,
                new WaypointList("cached", 7, List.of(waypoint("spawn", 10))));
        client.refreshCurrentCollections();
        var shared = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getWaypointByName("spawn");
        var local = client.collections().files(ClientWaypointSource.LOCAL).getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getWaypointByName("spawn");
        org.junit.jupiter.api.Assertions.assertTrue(shared.isRendered());
        org.junit.jupiter.api.Assertions.assertTrue(local.isRendered());
        client.setMarkerVisible(client.collections().selection(ClientWaypointSource.SHARED, DIMENSION, "cached", "spawn"), false);
        receive(WaypointData.waypoint(WaypointScope.SHARED, DIMENSION,
                new WaypointList("cached", 7, List.of(waypoint("spawn", 10))), waypoint("spawn", 10)));
        org.junit.jupiter.api.Assertions.assertFalse(shared.isRendered(), "Download must not enable a hidden marker");
        client.setMarkerVisible(client.collections().selection(ClientWaypointSource.SHARED, DIMENSION, "cached", "spawn"), true);
        client.onWaypointModification(new _959.server_waypoint.core.network.message.WaypointModificationMessage(WaypointScope.SHARED,
                DIMENSION, "cached", "cached", null, null, _959.server_waypoint.core.waypoint.WaypointModificationType.REMOVE_LIST, 8));
        org.junit.jupiter.api.Assertions.assertFalse(shared.isRendered());
        org.junit.jupiter.api.Assertions.assertTrue(local.isRendered());
    }

    @Test
    void sharedWorldResyncAndPrivateTombstoneKeepLocalAndNeverSendPrivateToMaps() throws Exception {
        prepareWorld();
        setStatic(WaypointClientMod.class, "currentDimensionName", DIMENSION);
        client.collections().beginConnection(java.util.UUID.randomUUID(), "test");
        client.collections().bindServer(directory, 1);
        client.collections().bindLocal(java.util.UUID.randomUUID(), new _959.server_waypoint.core.WaypointFilesManagerCore(directory.resolve("local")));
        client.collections().files(ClientWaypointSource.LOCAL).putWaypointList(DIMENSION,
                new WaypointList("cached", 7, List.of(waypoint("spawn", 10))));
        client.refreshCollectionScene(ClientWaypointSource.LOCAL);
        client.collections().apply(new _959.server_waypoint.core.network.message.WaypointCollectionStateMessage(WaypointScope.PRIVATE, true), client.collections().generation());
        receive(WaypointData.world(WaypointScope.PRIVATE, List.of(new DimensionWaypointData(DIMENSION,
                List.of(new WaypointList("cached", 7, List.of(waypoint("spawn", 10))))))));
        assertNull(xaero.lastEvent);
        assertNull(voxelMap.lastEvent);
        var privateMarker = client.collections().files(ClientWaypointSource.PRIVATE).getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getWaypointByName("spawn");
        var local = client.collections().files(ClientWaypointSource.LOCAL).getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getWaypointByName("spawn");
        int privateId = privateMarker.renderId, localId = local.renderId;
        org.junit.jupiter.api.Assertions.assertTrue(privateId >= 0 && localId >= 0 && privateId != localId);
        receive(WaypointData.world(WaypointScope.SHARED, List.of(new DimensionWaypointData(DIMENSION,
                List.of(new WaypointList("cached", 7, List.of(waypoint("spawn", 10))))))));
        assertEquals(privateId, privateMarker.renderId);
        assertEquals(localId, local.renderId);
        var shared = client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getWaypointByName("spawn");
        client.onWaypointModification(new _959.server_waypoint.core.network.message.WaypointModificationMessage(WaypointScope.PRIVATE,
                DIMENSION, "cached", "cached", null, null, _959.server_waypoint.core.waypoint.WaypointModificationType.REMOVE_LIST, 8));
        org.junit.jupiter.api.Assertions.assertFalse(privateMarker.isRendered());
        org.junit.jupiter.api.Assertions.assertTrue(shared.isRendered());
        assertEquals(localId, local.renderId);
        assertEquals(ClientWaypointSyncEvent.Type.WORLD_REPLACED, xaero.lastEvent.type());
    }

    @Test
    void rendererCapacityDropsOnlyRenderHandlesAndKeepsAllCollectionData() throws Exception {
        prepareWorld();
        setStatic(WaypointClientMod.class, "currentDimensionName", DIMENSION);
        var renderer = _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.class;
        _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.init();
        _959.server_waypoint.common.client.render.OptimizedWaypointRenderer.clearScene();
        drainRenderer(renderer);
        client.collections().beginConnection(java.util.UUID.randomUUID(), "test");
        client.collections().bindServer(directory, 1);
        client.collections().bindLocal(java.util.UUID.randomUUID(), new _959.server_waypoint.core.WaypointFilesManagerCore(directory.resolve("local")));
        var shared = java.util.stream.IntStream.range(0, 9999).mapToObj(index -> waypoint("Marker " + index, index)).toList();
        client.putWaypointList(DIMENSION, new WaypointList("Many", 7, shared));
        for (var source : java.util.List.of(ClientWaypointSource.PRIVATE, ClientWaypointSource.LOCAL)) {
            client.collections().files(source).putWaypointList(DIMENSION, new WaypointList("Many", 7, List.of(waypoint("Base", 0))));
        }
        for (var source : ClientWaypointSource.values()) client.collections().markSnapshotApplied(source);
        client.refreshCurrentCollections();
        drainRenderer(renderer);
        Field count = renderer.getDeclaredField("count");
        count.setAccessible(true);
        assertEquals(10000, count.getInt(null));
        assertEquals(9999, client.getWaypointFileManager(DIMENSION).getWaypointListByName("Many").size());
        assertEquals(1, client.collections().files(ClientWaypointSource.PRIVATE).getWaypointFileManager(DIMENSION).getWaypointListByName("Many").size());
        var local = client.collections().files(ClientWaypointSource.LOCAL).getWaypointFileManager(DIMENSION).getWaypointListByName("Many");
        assertEquals(1, local.size());
        org.junit.jupiter.api.Assertions.assertFalse(local.getWaypointByName("Base").isRendered());
        org.junit.jupiter.api.Assertions.assertTrue(local.isShow());
        for (var source : ClientWaypointSource.values()) client.collections().files(source).saveAllWaypointFiles();
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(client.collections().files(ClientWaypointSource.LOCAL)
                .getWaypointFileManager(DIMENSION).getDimensionFile()));
    }

    private static void drainRenderer(Class<?> renderer) throws Exception {
        Field queue = renderer.getDeclaredField("queue");
        queue.setAccessible(true);
        var pending = (java.util.Queue<?>) queue.get(null);
        Class<?> command = java.util.Arrays.stream(renderer.getDeclaredClasses()).filter(type -> type.getSimpleName().equals("WaypointRendererCommand")).findFirst().orElseThrow();
        var process = renderer.getDeclaredMethod("processCommand", command);
        process.setAccessible(true);
        Object value;
        while ((value = pending.poll()) != null) process.invoke(null, value);
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
        receive(WaypointData.waypointList(WaypointScope.SHARED, DIMENSION, WaypointList.build("cached", 7)));

        assertNull(xaero.lastEvent);
        assertNull(voxelMap.lastEvent);
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(name, "S", new WaypointPos(x, 64, 0), 0xFFFFFF, 0, false);
    }

    @Test
    void automaticUpdatesStillRespectDisabledAutoSync() {
        receive(WaypointData.updates(WaypointScope.SHARED, List.of(new DimensionWaypointData(DIMENSION,
                List.of(WaypointList.build("cached", 8))))));

        assertNull(xaero.lastEvent);
        assertNull(voxelMap.lastEvent);
        assertEquals(8, client.getWaypointFileManager(DIMENSION).getWaypointListByName("cached").getSyncNum());
    }

    @ParameterizedTest
    @EnumSource(WaypointScope.class)
    void appliedWaypointDeltasNotifyOncePerMutation(WaypointScope scope) throws Exception {
        syncNotificationFixture(scope, List.of());
        var lastMapEvent = xaero.lastEvent;
        receive(delta(scope, ADD, null, namedWaypoint("home", "Home Base"), 8));
        receive(delta(scope, UPDATE, "home", namedWaypoint("camp", "Camp Alpha"), 9));
        receive(delta(scope, REMOVE, "camp", null, 10));

        assertEquals(List.of(Map.entry(ADD, "Home Base"), Map.entry(UPDATE, "Camp Alpha"),
                Map.entry(REMOVE, "Camp Alpha")), updateNotifications);
        assertEquals(10, notificationList(scope).getSyncNum());
        assertTrue(notificationList(scope).isEmpty());
        assertSame(lastMapEvent, xaero.lastEvent);
    }

    @ParameterizedTest
    @EnumSource(WaypointScope.class)
    void replayedOrUnappliedWaypointDeltasStaySilent(WaypointScope scope) throws Exception {
        syncNotificationFixture(scope, List.of());
        var add = delta(scope, ADD, null, namedWaypoint("home", "Home Base"), 8);
        receive(add);
        receive(add);
        receive(delta(scope, UPDATE, "missing", namedWaypoint("camp", "Camp Alpha"), 9));
        receive(delta(scope, REMOVE, "missing", null, 9));
        receive(delta(scope, REMOVE, "home", null, 10));

        assertEquals(List.of(Map.entry(ADD, "Home Base")), updateNotifications);
        assertEquals(8, notificationList(scope).getSyncNum());
        assertNotNull(notificationList(scope).getWaypointByName("home"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editResultAndDeltaNotifyOnlyOnceInEitherOrder(boolean resultFirst) throws Exception {
        syncNotificationFixture(WaypointScope.PRIVATE, List.of(namedWaypoint("home", "Home Base")));
        var request = new AtomicReference<WaypointEditRequestMessage>();
        var access = new ClientWaypointAccess(client.collections(), client.localProfiles(), new ClientWaypointAccess.Transport() {
            public boolean connected() { return true; }
            public String currentDimension() { return DIMENSION; }
            public int commandLimit() { return 32767; }
            public List<String> dimensions() { return List.of(DIMENSION); }
            public boolean allowed(ClientWaypointSource source, ClientWaypointAccess.Action action) { return true; }
            public boolean command(String command) { throw new AssertionError("Unexpected command"); }
            public boolean edit(WaypointEditRequestMessage message) {
                request.set(message);
                return true;
            }
        });
        Field accessField = WaypointClientMod.class.getDeclaredField("access");
        accessField.setAccessible(true);
        accessField.set(client, access);
        var selection = access.capture(ClientWaypointSource.PRIVATE, DIMENSION, "cached", "home");
        var completion = client.access().edit(selection, 7, WaypointPatch.empty());
        var edited = namedWaypoint("camp", "Camp Alpha");
        var result = new WaypointEditResultMessage(WaypointScope.PRIVATE, request.get().requestId(),
                EditResultStatus.SUCCESS, DIMENSION, "cached", "home", edited, 8);
        var update = delta(WaypointScope.PRIVATE, UPDATE, "home", edited, 8);

        if (resultFirst) {
            receive(result);
            assertTrue(updateNotifications.isEmpty());
            receive(update);
        } else {
            receive(update);
            receive(result);
        }
        assertTrue(completion.isDone());
        assertEquals(ClientWaypointAccess.Status.SAVED, completion.join().status());
        assertEquals(List.of(Map.entry(UPDATE, "Camp Alpha")), updateNotifications);
    }

    @Test
    void snapshotsListsAndStalePrivatePacketsStaySilent() throws Exception {
        syncNotificationFixture(WaypointScope.SHARED, List.of());
        syncNotificationFixture(WaypointScope.PRIVATE, List.of(namedWaypoint("home", "Home Base")));
        receive(delta(WaypointScope.SHARED, REMOVE_LIST, null, null, 8));
        assertNull(notificationList(WaypointScope.SHARED));
        var lastMapEvent = xaero.lastEvent;

        var lateUpdate = delta(WaypointScope.PRIVATE, ADD, null, namedWaypoint("camp", "Camp Alpha"), 8);
        receive(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, false));
        receive(lateUpdate);
        long oldGeneration = client.collections().generation();
        bindPrivateNotificationFixture(2, List.of());
        for (var frame : ChunkedMessageManager.createTransfer(lateUpdate, false)) {
            client.onMessageChunk(frame, oldGeneration);
        }
        assertTrue(notificationList(WaypointScope.PRIVATE).isEmpty());
        assertTrue(updateNotifications.isEmpty());
        assertSame(lastMapEvent, xaero.lastEvent);
    }

    private void syncNotificationFixture(WaypointScope scope, List<SimpleWaypoint> waypoints) throws Exception {
        prepareWorld();
        setStatic(WaypointClientMod.class, "networkState", WaypointClientMod.ClientNetworkState.SYNC_FINISHED);
        if (scope == WaypointScope.PRIVATE) {
            client.collections().beginConnection(UUID.randomUUID(), "test");
            bindPrivateNotificationFixture(1, waypoints);
        } else {
            receive(WaypointData.world(scope, List.of(new DimensionWaypointData(DIMENSION,
                    List.of(new WaypointList("cached", 7, waypoints))))));
        }
    }

    private void bindPrivateNotificationFixture(int backend, List<SimpleWaypoint> waypoints) {
        client.collections().bindServer(directory, backend);
        receive(new WaypointCollectionStateMessage(WaypointScope.PRIVATE, true));
        receive(WaypointData.world(WaypointScope.PRIVATE, List.of(new DimensionWaypointData(DIMENSION,
                List.of(new WaypointList("cached", 7, waypoints))))));
    }

    private WaypointList notificationList(WaypointScope scope) {
        return client.collections().files(ClientWaypointSource.fromScope(scope))
                .getWaypointFileManager(DIMENSION).getWaypointListByName("cached");
    }

    private static WaypointModificationMessage delta(WaypointScope scope, WaypointModificationType type,
                                                      String previousName, SimpleWaypoint waypoint, int revision) {
        return new WaypointModificationMessage(scope, DIMENSION, "cached", "cached", previousName, waypoint, type, revision);
    }

    private static SimpleWaypoint namedWaypoint(String name, String displayName) {
        return new SimpleWaypoint(name, displayName, "S", new WaypointPos(10, 64, 0), 0xFFFFFF, 0,
                false, List.of(), "");
    }

    private void assertRefreshed(ClientWaypointSyncEvent.Type type) {
        assertNotNull(xaero.lastEvent);
        assertNotNull(voxelMap.lastEvent);
        assertEquals(type, xaero.lastEvent.type());
        assertEquals(type, voxelMap.lastEvent.type());
    }

    private void receive(ChunkedMessage message) {
        for (var frame : ChunkedMessageManager.createTransfer(message, false)) {
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
