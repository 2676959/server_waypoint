package _959.server_waypoint.mixin.xaeros_worldmap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class XaerosWorldMapWaypointMixinTest {
    @Test
    void nativeEditorCanStillLookUpTheOriginalSyncedSet() throws ReflectiveOperationException {
        String rawSetName = "sw\u241FBases";
        WaypointSet syncedSet = WaypointSet.Builder.begin().setName(rawSetName).build();
        WaypointSet personalSet = WaypointSet.Builder.begin().setName("Bases").build();
        Map<String, WaypointSet> sets = Map.of(rawSetName, syncedSet, "Bases", personalSet);

        String returnedKey = applyReturnHooks("getSetName", rawSetName);

        assertEquals(rawSetName, returnedKey);
        assertSame(syncedSet, sets.get(returnedKey),
                "Xaero's native editor must look up the owning set, even when a personal set shares its label");
    }

    @Test
    void displayedWaypointNamesStillHideTheOwnershipMarker() throws ReflectiveOperationException {
        assertEquals("Home", applyReturnHooks("getName", "sw\u241FHome"));
        assertEquals("Personal", applyReturnHooks("getName", "Personal"));
    }

    /** Exercises our registered return callbacks without starting the Minecraft renderer. */
    private static String applyReturnHooks(String getter, String original) throws ReflectiveOperationException {
        CallbackInfoReturnable<String> callback = new CallbackInfoReturnable<>(getter, true, original);
        XaerosWorldMapWaypointMixin mixin = new XaerosWorldMapWaypointMixin();
        for (Method method : XaerosWorldMapWaypointMixin.class.getDeclaredMethods()) {
            Inject injection = method.getAnnotation(Inject.class);
            if (injection != null && Arrays.asList(injection.method()).contains(getter)) {
                method.setAccessible(true);
                method.invoke(mixin, callback);
            }
        }
        return callback.getReturnValue();
    }
}
