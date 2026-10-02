package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.render.WaypointItemTint;
//? if >= 1.21.6 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.render.GuiRenderer;
//? if >= 26 {
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
//?} else {
/*import net.minecraft.client.gui.render.state.GuiItemRenderState;
*///?}
import org.spongepowered.asm.mixin.injection.At;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
//? if >= 1.21.6 {
@Mixin(GuiRenderer.class)
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer")
*///?}
public abstract class GuiRendererItemAlphaMixin {
    //? if >= 1.21.6 {
    @ModifyExpressionValue(method = "submitBlitFromItemAtlas",
            at = @At(value = "CONSTANT", args = "intValue=-1"))
    private int serverWaypoint$tintWaypointItem(int original,
                                                 @Local(argsOnly = true) GuiItemRenderState itemState) {
        int tint = ((WaypointItemTint) (Object) itemState).serverWaypoint$getTint();
        return tint == -1 ? original : tint;
    }
    //?}
}
