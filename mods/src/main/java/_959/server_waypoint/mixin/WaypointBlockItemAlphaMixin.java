package _959.server_waypoint.mixin;

//? if <= 1.21 && fabric {
/*import _959.server_waypoint.common.client.render.WaypointItemAlpha;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.injection.At;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
//? if <= 1.21 && fabric {
/*@Mixin(ItemRenderer.class)
*///?} else {
@Mixin(targets = "net.minecraft.client.renderer.entity.ItemRenderer")
//?}
public class WaypointBlockItemAlphaMixin {
    //? if <= 1.21 && fabric {
    /*@ModifyExpressionValue(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderType(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/client/renderer/RenderType;")
    )
    private RenderType serverWaypoint$translucentBlockIcon(RenderType original) {
        // The cutout sheet disables blending, so shader alpha alone cannot fade block icons.
        return WaypointItemAlpha.currentTint() != -1 && original == Sheets.cutoutBlockSheet()
                ? Sheets.translucentCullBlockSheet() : original;
    }
    *///?}
}
