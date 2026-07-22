package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
//? if >= 26
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameNavigationMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationItemFrameInsertion(
            Player player,
            InteractionHand hand,
            //? if >= 26
            Vec3 hitLocation,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
