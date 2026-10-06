package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.mixin.xaeros_minimap.MinimapWorldStateUpdaterMixin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedWorldMapModSyncTest {
    private final Map<Field, Object> previousValues = new LinkedHashMap<>();

    @BeforeEach
    void enterIntegratedWorld() throws ReflectiveOperationException {
        var constructor = ClientConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        setStatic(WaypointClientMod.class, "clientConfig", constructor.newInstance());
        setStatic(WaypointClientMod.class, "networkState", WaypointClientMod.ClientNetworkState.SYNC_FINISHED);
        // Automatic entry must not access the client model or either map mod's storage.
        setStatic(WaypointClientMod.class, "INSTANCE", null);
        setStatic(WaypointServerMod.class, "runsWithClient", true);
        setStatic(WaypointClientMod.class, "isXaerosMinimapReady", true);
        setStatic(ClientConfig.class, "isXaerosMinimapLoaded", true);
        setStatic(ClientConfig.class, "isVoxelMapLoaded", true);
    }

    @AfterEach
    void restoreState() throws IllegalAccessException {
        for (var entry : previousValues.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    void xaeroWorldReadinessDoesNotStartFullSync() throws ReflectiveOperationException {
        setStatic(WaypointClientMod.class, "isXaerosMinimapReady", false);
        var callback = MinimapWorldStateUpdaterMixin.class.getDeclaredMethod(
                "injectOnServerLevelId", int.class, CallbackInfo.class);
        callback.setAccessible(true);

        assertDoesNotThrow(() -> callback.invoke(new MinimapWorldStateUpdaterMixin(), 1,
                new CallbackInfo("onServerLevelId", false)));
        assertTrue(WaypointClientMod.isXaerosMinimapReady);
    }

    @Test
    void initialSyncEventDoesNotStartXaeroFullSync() {
        assertDoesNotThrow(() -> MapModIntegrations.onClientWaypointSync(
                ClientWaypointSyncEvent.allSynced(), null));
    }

    //? if fabric {
    @Test
    void initialSyncEventDoesNotStartVoxelMapFullSync() throws ReflectiveOperationException {
        setStatic(ClientConfig.class, "isXaerosMinimapLoaded", false);
        assertDoesNotThrow(() -> MapModIntegrations.onClientWaypointSync(
                ClientWaypointSyncEvent.allSynced(), null));
    }
    //?}

    private void setStatic(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        if (!previousValues.containsKey(field)) {
            previousValues.put(field, field.get(null));
        }
        field.set(null, value);
    }
}
