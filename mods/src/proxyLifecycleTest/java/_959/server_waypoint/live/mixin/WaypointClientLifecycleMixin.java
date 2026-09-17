package _959.server_waypoint.live.mixin;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.live.ProxyLifecycleTestControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WaypointClientMod.class)
public class WaypointClientLifecycleMixin {
    @Inject(method = "onLeaveServer", at = @At("HEAD"))
    private void sw$beforeLeave(CallbackInfo ci) {
        ProxyLifecycleTestControl.beforeLeave((WaypointClientMod) (Object) this);
    }

    @Inject(method = "onLeaveServer", at = @At("RETURN"))
    private void sw$afterLeave(CallbackInfo ci) {
        ProxyLifecycleTestControl.afterLeave((WaypointClientMod) (Object) this);
    }

    @Inject(method = "onJoinServer", at = @At("HEAD"))
    private void sw$beforeJoin(CallbackInfo ci) {
        ProxyLifecycleTestControl.beforeJoin((WaypointClientMod) (Object) this);
    }

    @Inject(method = "onJoinServer", at = @At("RETURN"))
    private void sw$afterJoin(CallbackInfo ci) {
        ProxyLifecycleTestControl.afterJoin((WaypointClientMod) (Object) this);
    }

    @Inject(method = "onServerHandshake", at = @At("HEAD"))
    private void sw$beforeHandshake(ServerHandshakeBuffer handshake, CallbackInfo ci) {
        ProxyLifecycleTestControl.beforeHandshake(
                (WaypointClientMod) (Object) this,
                handshake
        );
    }

    @Inject(method = "onServerHandshake", at = @At("RETURN"))
    private void sw$afterHandshake(ServerHandshakeBuffer handshake, CallbackInfo ci) {
        ProxyLifecycleTestControl.afterHandshake(
                (WaypointClientMod) (Object) this,
                handshake
        );
    }

    @Inject(method = "onUpdatesBundle", at = @At("RETURN"))
    private void sw$afterUpdatesBundle(List<DimensionWaypointData> updates, CallbackInfo ci) {
        ProxyLifecycleTestControl.afterSynchronization(
                (WaypointClientMod) (Object) this,
                "updates"
        );
    }

    @Inject(method = "onWorldWaypoint", at = @At("RETURN"))
    private void sw$afterWorldWaypoint(List<DimensionWaypointData> dimensions, CallbackInfo ci) {
        ProxyLifecycleTestControl.afterWorldSynchronization(
                (WaypointClientMod) (Object) this
        );
    }

    @Inject(method = "onDimensionChange", at = @At("RETURN"))
    private static void sw$afterDimensionChange(String dimensionName, CallbackInfo ci) {
        ProxyLifecycleTestControl.afterDimensionChange(dimensionName);
    }
}
