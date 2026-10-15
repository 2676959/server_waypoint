package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.WaypointItemAlpha;
import _959.server_waypoint.common.client.render.WaypointItemTint;
//? if >= 1.21.6 {
//? if >= 26 {
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
//?} else {
/*import net.minecraft.client.gui.render.state.GuiItemRenderState;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
//? if >= 1.21.6 {
@Mixin(GuiItemRenderState.class)
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.render.state.GuiItemRenderState")
*///?}
public abstract class GuiItemRenderStateMixin implements WaypointItemTint {
    @Unique
    private int serverWaypoint$tint = -1;

    //? if >= 1.21.6 {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void serverWaypoint$captureTint(CallbackInfo ci) {
        this.serverWaypoint$tint = WaypointItemAlpha.currentTint();
    }
    //?}

    @Override
    public int serverWaypoint$getTint() {
        return this.serverWaypoint$tint;
    }
}
