package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuNavigationMixin {
    @Shadow(/*? if >= 26 {*/remap = false/*?}*/)
    @Final
    public Container container;

    @Shadow(/*? if >= 26 {*/remap = false/*?}*/)
    @Final
    private ResultContainer resultContainer;

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationMapProcessing(Container changedContainer, CallbackInfo ci) {
        if (!ModNavigationHooks.isNavigationItem(this.container.getItem(0))) {
            return;
        }
        this.resultContainer.removeItemNoUpdate(2);
        ci.cancel();
    }
}
