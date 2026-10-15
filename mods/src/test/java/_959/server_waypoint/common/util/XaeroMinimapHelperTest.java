package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroMinimapHelperTest {
    private static final String DEFAULT_SET = "gui.xaero_default";
    private static final String SYNCED_SET = "sw\u241Ftest";
    private static final String SYNCED_WAYPOINT = "sw\u241Fserver waypoint";

    private ClientConfig previousConfig;
    private final Gson gson = new Gson();

    @BeforeEach
    void installConfig() throws Exception {
        previousConfig = WaypointClientMod.getClientConfig();
        configField().set(null, gson.fromJson("{}", ClientConfig.class));
    }

    @AfterEach
    void restoreConfig() throws Exception {
        configField().set(null, previousConfig);
    }

    private static Field configField() throws Exception {
        Field field = WaypointClientMod.class.getDeclaredField("clientConfig");
        field.setAccessible(true);
        return field;
    }

    private void setConfig(boolean directSync) throws Exception {
        ClientConfig config = gson.fromJson("{\"xaeroDefaultListDirectSync\":" + directSync + "}", ClientConfig.class);
        configField().set(null, gson.fromJson(gson.toJson(config), ClientConfig.class));
    }

    @Test
    void defaultSetHighlightsOwnedMarkerButNotPersonalDuplicate() throws Exception {
        WaypointSet set = WaypointSet.Builder.begin().setName(DEFAULT_SET).build();
        Waypoint owned = createWaypoint("sw\u241FSpawn");
        Waypoint personal = createWaypoint("Spawn");
        set.add(owned);
        set.add(personal);

        assertEquals(0x7F0D47A1, SyncedWaypointHighlight.xaerosWaypointBackground(set, owned));
        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(set, personal));
    }

    @Test
    void detachedSameNameMarkerCannotBorrowDefaultSetHighlight() throws Exception {
        WaypointSet set = WaypointSet.Builder.begin().setName(DEFAULT_SET).build();
        set.add(createWaypoint("sw\u241FSpawn"));
        Waypoint detached = createWaypoint("sw\u241FSpawn");

        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(set, detached));
    }

    @Test
    void markedNameInAnotherPersonalSetDoesNotReceiveDefaultOwnership() throws Exception {
        WaypointSet set = WaypointSet.Builder.begin().setName("Personal").build();
        Waypoint marked = createWaypoint("sw\u241FSpawn");
        set.add(marked);

        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(set, marked));
    }

    @Test
    void directDefaultSyncReplacesContentsAndKeepsOtherPersonalSets() throws Exception {
        setConfig(true);
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        world.addWaypointSet("Other personal set");
        world.getWaypointSet("Other personal set").add(createWaypoint("Keep"));
        world.setCurrentWaypointSetId(DEFAULT_SET);
        SimpleWaypoint server = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);

        assertTrue(XaeroMinimapHelper.replaceWaypointList(world,
                new WaypointList(DEFAULT_SET, 1, List.of(server)), XaeroMinimapHelperTest::createWaypoint));

        assertNull(world.getWaypointSet("sw\u241Fgui.xaero_default"));
        assertEquals(1, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals("sw\u241FSpawn", world.getWaypointSet(DEFAULT_SET).get(0).getName());
        assertEquals(1, world.getWaypointSet(DEFAULT_SET).get(0).getX());
        assertEquals(1, world.getWaypointSet("Other personal set").size());
        assertEquals(DEFAULT_SET, world.getCurrentWaypointSetId());
        assertEquals(0x7F0D47A1, SyncedWaypointHighlight.xaerosWaypointBackground(
                world.getWaypointSet(DEFAULT_SET), world.getWaypointSet(DEFAULT_SET).get(0)));
    }

    @Test
    void separateDefaultSyncPreservesPersonalDefaultContents() throws Exception {
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));

        assertTrue(XaeroMinimapHelper.replaceWaypointList(world,
                new WaypointList(DEFAULT_SET, 1, List.of()), XaeroMinimapHelperTest::createWaypoint));

        assertNotNull(world.getWaypointSet("sw\u241Fgui.xaero_default"));
        assertEquals("Personal", world.getWaypointSet(DEFAULT_SET).get(0).getName());
    }

    @Test
    void fullSyncUsesDefaultSetAndRemovesStaleSeparateCopy() throws Exception {
        setConfig(true);
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.addWaypointSet("sw\u241Fgui.xaero_default");
        world.setCurrentWaypointSetId("sw\u241Fgui.xaero_default");
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));

        assertTrue(XaeroMinimapHelper.replaceWaypointLists(world,
                List.of(new WaypointList(DEFAULT_SET, 1, List.of()))));

        assertNull(world.getWaypointSet("sw\u241Fgui.xaero_default"));
        assertEquals(0, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals(DEFAULT_SET, world.getCurrentWaypointSetId());
    }

    @Test
    void fullSyncWithoutDefaultListOnlyRemovesMarkedDefaultWaypoints() throws Exception {
        setConfig(true);
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("sw\u241FSpawn"));
        world.setCurrentWaypointSetId(DEFAULT_SET);

        assertTrue(XaeroMinimapHelper.replaceWaypointLists(world, List.of()));

        assertEquals(1, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals("Personal", world.getWaypointSet(DEFAULT_SET).get(0).getName());
        assertEquals(DEFAULT_SET, world.getCurrentWaypointSetId());
    }

    @Test
    void switchingBackToSeparateSetCleansMarkedEntriesOnly() throws Exception {
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("sw\u241FSpawn"));

        XaeroMinimapHelper.replaceWaypointList(world,
                new WaypointList(DEFAULT_SET, 1, List.of()), XaeroMinimapHelperTest::createWaypoint);

        assertEquals(1, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals("Personal", world.getWaypointSet(DEFAULT_SET).get(0).getName());
        assertNotNull(world.getWaypointSet("sw\u241Fgui.xaero_default"));
    }

    @Test
    void incrementalDefaultUpdateDoesNotDeduplicatePersonalEntries() throws Exception {
        setConfig(true);
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        WaypointSet set = world.getWaypointSet(DEFAULT_SET);
        set.add(createWaypoint("Personal"));
        set.add(createWaypoint("Personal"));
        set.add(createWaypoint("sw\u241FSpawn"));

        XaeroMinimapHelper.replaceSyncedWaypoint(set,
                new SimpleWaypoint("Spawn", "S", 9, 2, 3, 0, 0, false), XaeroMinimapHelperTest::createWaypoint);

        assertEquals(3, set.size());
        assertEquals("sw\u241FSpawn", set.get(2).getName());
        assertEquals(9, set.get(2).getX());
        assertTrue(XaeroMinimapHelper.removeSyncedWaypoint(set, "Spawn"));
        assertEquals(2, set.size());
        assertFalse(XaeroMinimapHelper.removeSyncedWaypoint(set, "Personal"));
    }

    @Test
    void deletingDirectDefaultListRetainsDefaultSetAndPersonalEntries() throws Exception {
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.setCurrentWaypointSetId(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("sw\u241FSpawn"));

        XaeroMinimapHelper.removeSyncedWaypointSet(world, DEFAULT_SET);

        assertEquals(1, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals("Personal", world.getWaypointSet(DEFAULT_SET).get(0).getName());
        assertNotNull(world.getCurrentWaypointSet());
    }

    @Test
    void deletingDefaultListAfterChangingModeCleansBothOwnedCopies() throws Exception {
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.addWaypointSet("sw\u241Fgui.xaero_default");
        world.setCurrentWaypointSetId("sw\u241Fgui.xaero_default");
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("sw\u241FSpawn"));

        XaeroMinimapHelper.removeSyncedWaypointSet(world, "sw\u241Fgui.xaero_default");

        assertNull(world.getWaypointSet("sw\u241Fgui.xaero_default"));
        assertEquals(1, world.getWaypointSet(DEFAULT_SET).size());
        assertEquals(DEFAULT_SET, world.getCurrentWaypointSetId());
    }

    @Test
    void unrepresentableDefaultWaypointDoesNotClearPersonalContents() throws Exception {
        setConfig(true);
        MinimapWorld world = createMinimapWorld();
        world.addWaypointSet(DEFAULT_SET);
        world.getWaypointSet(DEFAULT_SET).add(createWaypoint("Personal"));
        SimpleWaypoint waypoint = new SimpleWaypoint("A\u241FB", "A", 1, 2, 3, 0, 0, false);

        assertFalse(XaeroMinimapHelper.replaceWaypointList(world,
                new WaypointList(DEFAULT_SET, 1, List.of(waypoint)), XaeroMinimapHelperTest::createWaypoint));

        assertEquals("Personal", world.getWaypointSet(DEFAULT_SET).get(0).getName());
    }

    @Test
    void skippedListDoesNotReportSuccessfulSync() throws ReflectiveOperationException {
        MinimapWorld world = createMinimapWorld();

        assertFalse(XaeroMinimapHelper.replaceWaypointList(world,
                new WaypointList("ambiguous\u241Flist", 1, List.of()),
                XaeroMinimapHelperTest::createWaypoint));
        assertNull(world.getWaypointSet("sw\u241Fambiguous\u241Flist"));
    }

    @Test
    void partiallySyncedDimensionDoesNotReportFullSuccess() throws ReflectiveOperationException {
        MinimapWorld world = createMinimapWorld();

        assertFalse(XaeroMinimapHelper.replaceWaypointLists(world, List.of(
                new WaypointList("ambiguous\u241Flist", 1, List.of()),
                new WaypointList("valid", 1, List.of()))));
        assertNotNull(world.getWaypointSet("sw\u241Fvalid"));
        assertTrue(XaeroMinimapHelper.replaceWaypointLists(world,
                List.of(new WaypointList("valid", 1, List.of()))));
    }

    @Test
    void removalReportsChangesOnceAndPreservesOtherWaypoints() throws ReflectiveOperationException {
        WaypointSet set = WaypointSet.Builder.begin().setName(SYNCED_SET).build();
        set.add(createWaypoint("server waypoint"));
        set.add(createWaypoint(SYNCED_WAYPOINT));
        set.add(createWaypoint("other"));

        assertTrue(XaeroMinimapHelper.removeSyncedWaypoint(set, "server waypoint"));
        assertFalse(XaeroMinimapHelper.removeSyncedWaypoint(set, "server waypoint"));
        assertEquals(1, set.size());
        assertEquals("other", set.get(0).getName());
    }

    @Test
    void removingSyncedWaypointSetRemovesPlainAndLegacyWaypoints() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        minimapWorld.addWaypointSet(DEFAULT_SET);
        WaypointSet waypointSet = WaypointSet.Builder.begin().setName(SYNCED_SET).build();
        waypointSet.add(createWaypoint(SYNCED_WAYPOINT));
        waypointSet.add(createWaypoint("local waypoint"));
        minimapWorld.addWaypointSet(waypointSet);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertNull(minimapWorld.getWaypointSet(SYNCED_SET));
        assertEquals(DEFAULT_SET, minimapWorld.getCurrentWaypointSetId());
    }

    @Test
    void removingEmptySyncedWaypointSetSelectsAnExistingSet() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        minimapWorld.addWaypointSet(DEFAULT_SET);
        minimapWorld.addWaypointSet(SYNCED_SET);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertNull(minimapWorld.getWaypointSet(SYNCED_SET));
        assertEquals(DEFAULT_SET, minimapWorld.getCurrentWaypointSetId());
        assertNotNull(minimapWorld.getCurrentWaypointSet());
    }

    @Test
    void removingAlreadyMissingCurrentWaypointSetRepairsSelection() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        minimapWorld.addWaypointSet(DEFAULT_SET);
        minimapWorld.setCurrentWaypointSetId(SYNCED_SET);

        XaeroMinimapHelper.removeSyncedWaypointSet(minimapWorld, SYNCED_SET);

        assertEquals(DEFAULT_SET, minimapWorld.getCurrentWaypointSetId());
        assertNotNull(minimapWorld.getCurrentWaypointSet());
    }

    @Test
    void replacingListPreservesEquivalentWaypointInPersonalSet() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint waypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet localSet = WaypointSet.Builder.begin().setName("test").build();
        localSet.add(createWaypoint(waypoint, waypoint.name()));
        minimapWorld.addWaypointSet(localSet);
        minimapWorld.setCurrentWaypointSetId("test");

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(waypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(localSet, minimapWorld.getWaypointSet("test"));
        assertEquals(1, localSet.size());
        WaypointSet syncedSet = minimapWorld.getWaypointSet("sw\u241Ftest");
        assertNotNull(syncedSet);
        assertEquals(1, syncedSet.size());
        assertEquals("Spawn", syncedSet.get(0).getName());
        assertEquals(0x7F0D47A1, SyncedWaypointHighlight.xaerosWaypointBackground(syncedSet, syncedSet.get(0)));
        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(localSet, localSet.get(0)));
    }

    @Test
    void doesNotHighlightUnrelatedRowsDisplayedAlongsideManagedSetWaypoints() {
        SimpleWaypoint waypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        Waypoint managed = createWaypoint(waypoint, "Spawn");
        Waypoint unrelated = createWaypoint(waypoint, "Spawn");
        WaypointSet syncedSet = WaypointSet.Builder.begin().setName("sw\u241Ftest").build();
        syncedSet.add(managed);

        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(syncedSet, unrelated));
        assertEquals(0, SyncedWaypointHighlight.xaerosWaypointBackground(syncedSet, null));
        assertEquals(0x7F0D47A1, SyncedWaypointHighlight.xaerosWaypointBackground(syncedSet, managed));
    }

    @Test
    void replacingListMigratesLegacyAndReplacesPlainWaypointIdentity() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint localWaypoint = new SimpleWaypoint("Spawn", "S", 10, 2, 3, 0, 0, false);
        SimpleWaypoint serverWaypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet syncedSet = WaypointSet.Builder.begin().setName("sw\u241Ftest").build();
        syncedSet.add(createWaypoint(localWaypoint, localWaypoint.name()));
        syncedSet.add(createWaypoint(serverWaypoint, "sw\u241FSpawn"));
        minimapWorld.addWaypointSet(syncedSet);

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(serverWaypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(1, syncedSet.size());
        assertEquals("Spawn", syncedSet.get(0).getName());
        assertEquals(1, syncedSet.get(0).getX());
    }

    @Test
    void replacingListRemovesDeletedPlainWaypointFromManagedSet() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint retained = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet syncedSet = WaypointSet.Builder.begin().setName(SYNCED_SET).build();
        syncedSet.add(createWaypoint(retained, retained.name()));
        syncedSet.add(createWaypoint("Deleted"));
        minimapWorld.addWaypointSet(syncedSet);

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(retained)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(1, syncedSet.size());
        assertEquals("Spawn", syncedSet.get(0).getName());
    }

    @Test
    void replacingListPreservesConflictingLocalWaypoint() throws ReflectiveOperationException {
        MinimapWorld minimapWorld = createMinimapWorld();
        SimpleWaypoint localWaypoint = new SimpleWaypoint("Spawn", "S", 10, 2, 3, 0, 0, false);
        SimpleWaypoint serverWaypoint = new SimpleWaypoint("Spawn", "S", 1, 2, 3, 0, 0, false);
        WaypointSet localSet = WaypointSet.Builder.begin().setName("test").build();
        localSet.add(createWaypoint(localWaypoint, localWaypoint.name()));
        minimapWorld.addWaypointSet(localSet);

        XaeroMinimapHelper.replaceWaypointList(
                minimapWorld,
                new WaypointList("test", 1, List.of(serverWaypoint)),
                XaeroMinimapHelperTest::createWaypoint
        );

        assertEquals(localSet, minimapWorld.getWaypointSet("test"));
        assertEquals(1, localSet.size());
        assertEquals(10, localSet.get(0).getX());
        WaypointSet syncedSet = minimapWorld.getWaypointSet("sw\u241Ftest");
        assertNotNull(syncedSet);
        assertEquals(1, syncedSet.size());
        assertEquals("Spawn", syncedSet.get(0).getName());
    }

    private static MinimapWorld createMinimapWorld() throws ReflectiveOperationException {
        //? if >= 1.21.5 {
        Constructor<MinimapWorld> constructor = MinimapWorld.class.getDeclaredConstructor(
                MinimapWorldContainer.class,
                String.class,
                ResourceKey.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, "test", null);
        //?} else {
        /*return new MinimapWorld(null, "test", null) {
        };
        *///?}
    }

    private static Waypoint createWaypoint(String name) throws ReflectiveOperationException {
        return createWaypoint(new SimpleWaypoint(name, "", 0, 0, 0, 0, 0, false), name);
    }

    private static Waypoint createWaypoint(SimpleWaypoint waypoint, String name) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            TestWaypoint testWaypoint = (TestWaypoint) unsafeClass
                    .getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, TestWaypoint.class);
            testWaypoint.initialize(waypoint, name);
            return testWaypoint;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create a Xaero waypoint test double", e);
        }
    }

    private static final class TestWaypoint extends Waypoint {
        private SimpleWaypoint waypoint;

        private TestWaypoint() {
            super(0, 0, 0, "", "", 0);
        }

        private void initialize(SimpleWaypoint waypoint, String name) {
            this.waypoint = waypoint;
            setX(waypoint.pos().x());
            setY(waypoint.pos().y());
            setZ(waypoint.pos().z());
            setName(name);
            setYaw(waypoint.yaw());
            setPurpose(xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL);
            setDisabled(false);
        }

        @Override
        public String getInitials() {
            return waypoint.initials();
        }

        @Override
        public int getColor() {
            return _959.server_waypoint.util.ColorUtils.rgbToClosestColorIndex(waypoint.rgb());
        }

        @Override
        public boolean isGlobal() {
            return waypoint.global();
        }
    }
}
