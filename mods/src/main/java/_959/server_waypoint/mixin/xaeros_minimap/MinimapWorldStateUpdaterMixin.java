package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.common.server.WaypointServerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;

import static _959.server_waypoint.common.client.WaypointClientMod.*;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.SYNC_FINISHED;
import static _959.server_waypoint.common.client.integrations.MapModIntegrations.syncNow;

@Mixin(value = MinimapWorldStateUpdater.class, remap = false)
public class MinimapWorldStateUpdaterMixin {

    @Inject(method = "onServerLevelId", at = @At(value = "TAIL"), remap = false)
    private void injectOnServerLevelId(int id, CallbackInfo ci) {
        isXaerosMinimapReady = true;
        if (!WaypointServerMod.runsWithClient()
                && getClientConfig().isAutoSyncToXaerosMinimap()
                && getNetworkState().equals(SYNC_FINISHED)) {
            syncNow(UploadTarget.XAERO, getInstance());
        }
    }
}
