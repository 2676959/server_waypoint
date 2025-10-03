package _959.server_waypoint.mixin;

import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeaconBlockEntityRenderer.class)
public class BeaconBeamRendererMixin {
    @Inject(method = "isInRenderDistance", at = @At(value = "HEAD"), cancellable = true)
    private void isInRenderDistance(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

}
