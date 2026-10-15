package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.WaypointItemTint;
//? if >= 1.21.6 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if >= 26 {
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//?} else {
/*import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
*///?}
import org.spongepowered.asm.mixin.injection.At;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
//? if >= 1.21.6 {
@Mixin(PictureInPictureRenderer.class)
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.render.pip.PictureInPictureRenderer")
*///?}
public abstract class OversizedWaypointItemAlphaMixin {
    //? if >= 1.21.6 {
    @ModifyExpressionValue(method = "blitTexture",
            at = @At(value = "CONSTANT", args = "intValue=-1"))
    private int serverWaypoint$tintOversizedItem(int original,
                                                  @Local(argsOnly = true) PictureInPictureRenderState renderState) {
        if (renderState instanceof OversizedItemRenderState itemState) {
            int tint = ((WaypointItemTint) (Object) itemState.guiItemRenderState()).serverWaypoint$getTint();
            return tint == -1 ? original : tint;
        }
        return original;
    }
    //?}
}
