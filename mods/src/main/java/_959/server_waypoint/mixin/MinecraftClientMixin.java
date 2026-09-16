package _959.server_waypoint.mixin;

import static _959.server_waypoint.common.client.WaypointClientMod.onDimensionChange;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(
        method = "updateLevelInEngines",
        at = @At(value = "HEAD") /*? if >= 26 {*/,
        remap = false /*?}*/
    )
    public void setWorld(ClientLevel world, CallbackInfo ci) {
        if (world == null) {
            // updateLevelInEngines(null) fires whenever the client leaves a level, which also happens on
            // seamless server switches (e.g. through a Velocity proxy) before the next server's level is
            // set. Reset the waypoint network state here so a stale dimension change from the previous
            // server is ignored and pending edits are flushed to the previous server's file.
            WaypointClientMod.ifPresent(instance -> instance.onLeaveServer());
            return;
        }
        //? if >= 1.21.11 {
        String worldName = world.dimension().identifier().toString();
        //?} else {
        /*String worldName = world.dimension().location().toString();
         */ //?}
        WaypointManagerScreen.resetWidgetStates();
        onDimensionChange(worldName);
    }
}
