package _959.server_waypoint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

import static _959.server_waypoint.common.client.WaypointClientMod.*;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.SYNC_FINISHED;
import static _959.server_waypoint.common.client.handlers.HandlerForXaerosMinimap.syncFromServerWaypointMod;

@Mixin(MinimapWorldStateUpdater.class)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {
        isXaerosMinimapReady = true;
        if (getClientConfig().isAutoSyncToXaerosMinimap() && getNetworkState().equals(SYNC_FINISHED)) {
            syncFromServerWaypointMod();
        }
    }
}
