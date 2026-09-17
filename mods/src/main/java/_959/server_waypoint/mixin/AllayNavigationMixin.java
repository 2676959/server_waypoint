package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Allay.class)
public abstract class AllayNavigationMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationItemHandoff(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "tick", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)
    private void sanitizeNavigationItems(CallbackInfo ci) {
        ModNavigationHooks.sanitizeAllay((Allay) (Object) this);
    }
}
