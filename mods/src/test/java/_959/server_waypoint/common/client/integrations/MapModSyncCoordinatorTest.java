package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapModSyncCoordinatorTest {
    private record Notification(Component chat, Component toast) {}

    private final MapStorage voxelMap = new MapStorage(UploadTarget.VOXELMAP);
    private final MapStorage xaero = new MapStorage(UploadTarget.XAERO);
    private final List<Notification> shown = new ArrayList<>();
    private final MapModSyncCoordinator coordinator = new MapModSyncCoordinator(List.of(xaero, voxelMap),
            new MapModSyncNotifications((chat, toast) -> shown.add(new Notification(chat, toast))));

    @Test
    void readyMapsShareOneNotification() {
        coordinator.onUnchangedSync(null, null, false);

        assertEquals(List.of(combinedNotification()), shown);
    }

    @ParameterizedTest
    @EnumSource(UploadTarget.class)
    void deferredFullSyncKeepsTheListNameInTheCombinedNotification(UploadTarget deferredTarget) {
        MapStorage deferred = deferredTarget == UploadTarget.VOXELMAP ? voxelMap : xaero;
        deferred.ready = false;
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.listReplaced("minecraft:overworld",
                WaypointList.build("Outposts", 1)), null, null, false);
        assertEquals(List.of(), shown);

        deferred.ready = true;
        coordinator.tick(null, null);

        assertEquals(List.of(new Notification(Component.translatable("server_waypoint.list.added.both_maps", "Outposts"),
                Component.translatable("server_waypoint.sync.toast.synced.both_maps"))), shown);
    }

    @Test
    void readyMapGetsItsOwnNotificationIfTheOtherMapNeverBecomesReady() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        for (int tick = 0; tick < 19; tick++) {
            coordinator.tick(null, null);
        }
        assertEquals(List.of(), shown);

        coordinator.tick(null, null);
        assertEquals(List.of(xaeroNotification()), shown);

        voxelMap.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of(xaeroNotification(), new Notification(
                Component.translatable("server_waypoint.all.added.voxelmap"),
                Component.translatable("server_waypoint.sync.toast.synced.voxelmap"))), shown);
    }

    @Test
    void attemptedSyncWithoutSuccessDoesNotNameThatMap() {
        voxelMap.reportSuccess = false;
        coordinator.onUnchangedSync(null, null, false);

        assertEquals(List.of(xaeroNotification()), shown);
    }

    @Test
    void aNewUpdateKeepsTheEarlierSyncNotificationDistinct() {
        xaero.ready = false;
        coordinator.onUnchangedSync(null, null, false);

        xaero.ready = true;
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.listReplaced("minecraft:overworld",
                WaypointList.build("Outposts", 1)), null, null, false);

        assertEquals(List.of(
                new Notification(Component.translatable("server_waypoint.all.added.voxelmap"),
                        Component.translatable("server_waypoint.sync.toast.synced.voxelmap")),
                new Notification(Component.translatable("server_waypoint.list.added.both_maps", "Outposts"),
                        Component.translatable("server_waypoint.sync.toast.synced.both_maps"))), shown);
    }

    @Test
    void clearingTheSessionDiscardsUndeliveredNotifications() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        coordinator.clearPending();
        voxelMap.ready = true;
        coordinator.onUnchangedSync(null, null, false);

        assertEquals(List.of(combinedNotification()), shown);
    }

    private static Notification combinedNotification() {
        return new Notification(Component.translatable("server_waypoint.all.added.both_maps"),
                Component.translatable("server_waypoint.sync.toast.synced.both_maps"));
    }

    private static Notification xaeroNotification() {
        return new Notification(Component.translatable("server_waypoint.all.added.xaeros"),
                Component.translatable("server_waypoint.sync.toast.synced.xaero"));
    }

    @Test
    void unchangedHandshakeRestoresManagedMarkersInBothMaps() {
        voxelMap.markers.add("sw:deleted");
        coordinator.onUnchangedSync(null, null, false);

        assertEquals(List.of("personal", "sw:Home"), voxelMap.markers);
        assertEquals(List.of("personal", "sw:Home"), xaero.markers);
    }

    @Test
    void xaeroWaitsForReadinessAndReconcilesOnlyOnce() {
        xaero.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        coordinator.tick(null, null);
        assertEquals(List.of("personal"), xaero.markers);

        xaero.model = List.of("sw:Latest");
        xaero.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Latest"), xaero.markers);

        xaero.markers.add("sw:local-edit");
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Latest", "sw:local-edit"), xaero.markers);
    }

    @Test
    void pendingMapsBecomeReadyIndependently() {
        xaero.ready = false;
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        xaero.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Home"), xaero.markers);
        assertEquals(List.of("personal"), voxelMap.markers);
        assertEquals(List.of(), shown);

        xaero.markers.add("sw:local-edit");
        voxelMap.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Home", "sw:local-edit"), xaero.markers);
        assertEquals(List.of("personal", "sw:Home"), voxelMap.markers);
        assertEquals(List.of(combinedNotification()), shown);
    }

    @Test
    void disablingXaeroAutoSyncCancelsItsDeferredWork() {
        xaero.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        xaero.enabled = false;
        coordinator.tick(null, null);
        xaero.enabled = true;
        xaero.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal"), xaero.markers);
    }

    @Test
    void unchangedHandshakeWaitsForReadinessAndReconcilesOnlyOnce() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        coordinator.tick(null, null);
        assertEquals(List.of("personal"), voxelMap.markers);

        voxelMap.ready = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Home"), voxelMap.markers);

        voxelMap.markers.add("sw:locally-edited");
        coordinator.tick(null, null);
        assertEquals(List.of("personal", "sw:Home", "sw:locally-edited"), voxelMap.markers);
    }

    @Test
    void delayedChangesCoalesceUsingTheLatestModel() {
        voxelMap.ready = false;
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.allSynced(), null, null, false);
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.listReplaced("minecraft:overworld",
                WaypointList.build("old", 1)), null, null, false);
        voxelMap.model = List.of("sw:Latest");
        voxelMap.ready = true;
        coordinator.tick(null, null);

        assertEquals(List.of("personal", "sw:Latest"), voxelMap.markers);
    }

    @Test
    void readyEventConsumesPendingReconciliationInsteadOfApplyingAnOldDelta() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        voxelMap.model = List.of("sw:Latest");
        voxelMap.ready = true;
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.listReplaced("minecraft:overworld",
                WaypointList.build("old", 1)), null, null, false);

        assertEquals(List.of("personal", "sw:Latest"), voxelMap.markers);
    }

    @Test
    void disabledAutoSyncDoesNotQueueReconciliation() {
        voxelMap.enabled = false;
        coordinator.onUnchangedSync(null, null, false);
        voxelMap.enabled = true;
        coordinator.tick(null, null);

        assertEquals(List.of("personal"), voxelMap.markers);
    }

    @Test
    void disablingAutoSyncCancelsDeferredWork() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        voxelMap.enabled = false;
        coordinator.tick(null, null);
        voxelMap.enabled = true;
        voxelMap.ready = true;
        coordinator.tick(null, null);

        assertEquals(List.of("personal"), voxelMap.markers);
    }

    @Test
    void uninstallingAMapCancelsDeferredWork() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        voxelMap.installed = false;
        coordinator.tick(null, null);
        voxelMap.installed = true;
        voxelMap.ready = true;
        coordinator.tick(null, null);

        assertEquals(List.of("personal"), voxelMap.markers);
    }

    @Test
    void integratedEntryDoesNotQueueOrRunFullSynchronization() {
        coordinator.onUnchangedSync(null, null, true);
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.allSynced(), null, null, true);
        coordinator.tick(null, null);

        assertEquals(List.of("personal"), voxelMap.markers);
        assertEquals(List.of("personal"), xaero.markers);
    }

    @Test
    void sessionResetCancelsDeferredWork() {
        voxelMap.ready = false;
        coordinator.onUnchangedSync(null, null, false);
        coordinator.clearPending();
        voxelMap.ready = true;
        coordinator.tick(null, null);

        assertEquals(List.of("personal"), voxelMap.markers);
    }

    @Test
    void normalUpdatesStillSynchronizeXaeroWhileVoxelMapWaits() {
        voxelMap.ready = false;
        coordinator.onAutomaticSync(ClientWaypointSyncEvent.allSynced(), null, null, false);

        assertEquals(List.of("personal", "sw:Home"), xaero.markers);
        assertEquals(List.of("personal"), voxelMap.markers);
    }

    @Test
    void idleTicksDoNotReadMapReadiness() {
        voxelMap.rejectReadinessChecks = true;
        xaero.rejectReadinessChecks = true;
        coordinator.tick(null, null);
        assertEquals(List.of("personal"), voxelMap.markers);
    }

    /** External map storage boundary; the coordinator itself is the production implementation. */
    private static final class MapStorage implements MapModIntegration {
        private final UploadTarget target;
        private final List<String> markers = new ArrayList<>(List.of("personal"));
        private List<String> model = List.of("sw:Home");
        private boolean ready = true;
        private boolean installed = true;
        private boolean enabled = true;
        private boolean reportSuccess = true;
        private boolean rejectReadinessChecks;

        private MapStorage(UploadTarget target) { this.target = target; }
        @Override public UploadTarget uploadTarget() { return target; }
        @Override public WaypointData collectUpload(UploadRequestBuffer request) { throw new AssertionError(); }
        @Override public boolean isInstalled() { return installed; }
        @Override public boolean isEnabled(ClientConfig config) { return enabled; }
        @Override public boolean isReady() {
            if (rejectReadinessChecks) throw new AssertionError("Idle tick accessed map storage");
            return ready;
        }
        @Override public void syncAll(WaypointClientMod client) {
            markers.removeIf(marker -> marker.startsWith("sw:"));
            markers.addAll(model);
        }
        @Override public SyncSuccess onClientWaypointSync(ClientWaypointSyncEvent event, WaypointClientMod client) {
            if (event.type() == ClientWaypointSyncEvent.Type.ALL_SYNCED) {
                syncAll(client);
                return reportSuccess ? SyncSuccess.FULL_SYNC : null;
            }
            markers.add("sw:old-delta");
            return new SyncSuccess("server_waypoint.list.added", event.listName());
        }
    }
}
