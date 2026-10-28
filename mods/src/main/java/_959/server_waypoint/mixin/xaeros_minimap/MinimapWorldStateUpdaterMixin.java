package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.client.WaypointClientMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

@Mixin(value = MinimapWorldStateUpdater.class, remap = false)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {
        // The coordinator drains pending work on the client tick, once both sides are ready.
        WaypointClientMod.isXaerosMinimapReady = true;
    }
}
