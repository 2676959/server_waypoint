package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompassItem.class)
public abstract class CompassItemNavigationMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationCompassLodestoneUse(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(context.getItemInHand())
                && context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.LODESTONE)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
