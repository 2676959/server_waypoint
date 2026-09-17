package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryNavigationMixin {
    @Shadow(/*? if >= 26 {*/remap = false/*?}*/)
    @Final
    public Player player;

    //? if >= 1.21.5 {
    @Shadow(/*? if >= 26 {*/remap = false/*?}*/)
    public abstract ItemStack getSelectedItem();
    //?} else {
    /*@Shadow()
    public abstract ItemStack getSelected();
    *///?}

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationHotbarDrop(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
        //? if >= 1.21.5 {
        ItemStack selected = this.getSelectedItem();
        //?} else {
        /*ItemStack selected = this.getSelected();
        *///?}
        if (ModNavigationHooks.shouldBlockSelectedDrop(this.player, selected)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
