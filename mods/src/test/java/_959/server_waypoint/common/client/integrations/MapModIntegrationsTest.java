package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.upload.UploadTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapModIntegrationsTest {
    @AfterEach
    void clearLoadedMapMods() {
        ClientConfig.isXaerosMinimapLoaded = false;
        ClientConfig.isVoxelMapLoaded = false;
    }

    @Test
    void xaerosMinimapIsSupportedOnEveryLoader() {
        assertTrue(MapModIntegrations.find(UploadTarget.XAERO).isPresent());
    }

    @Test
    void voxelMapIsSupportedOnlyWhereVoxelMapIsPinned() {
        //? if voxelmap {
        assertTrue(MapModIntegrations.find(UploadTarget.VOXELMAP).isPresent());
        //?} else {
        /*assertTrue(MapModIntegrations.find(UploadTarget.VOXELMAP).isEmpty());
        *///?}
    }

    @Test
    void installedStateFollowsTheLoadedFlag() {
        ClientConfig.isXaerosMinimapLoaded = true;
        assertTrue(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isInstalled());

        ClientConfig.isXaerosMinimapLoaded = false;
        assertFalse(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isInstalled());
    }

    @Test
    void syncNowSkipsAMapModThatIsNotInstalled() {
        assertFalse(MapModIntegrations.syncNow(UploadTarget.XAERO, null));
    }

    @Test
    void xaeroAutoSyncPreferenceIsIndependentOfReadiness() throws ReflectiveOperationException {
        var constructor = ClientConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ClientConfig config = constructor.newInstance();
        boolean previous = WaypointClientMod.isXaerosMinimapReady;
        try {
            ClientConfig.isXaerosMinimapLoaded = true;
            WaypointClientMod.isXaerosMinimapReady = false;
            assertTrue(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isEnabled(config));
            config.setAutoSyncToXaerosMinimap(false);
            assertFalse(MapModIntegrations.find(UploadTarget.XAERO).orElseThrow().isEnabled(config));
        } finally {
            WaypointClientMod.isXaerosMinimapReady = previous;
        }
    }

    //? if voxelmap {
    @Test
    void voxelMapIsNotReadyBeforeAWorldIsLoaded() {
        assertFalse(MapModIntegrations.find(UploadTarget.VOXELMAP).orElseThrow().isReady());
    }
    //?}
}
