package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityNavigationMixin {
    @Inject(method = "setItem", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void discardNavigationItemWhenAssigned(ItemStack item, CallbackInfo ci) {
        if (ModNavigationHooks.isNavigationItem(item)) {
            ((ItemEntity) (Object) this).discard();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void discardNavigationItemBeforeTick(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (ModNavigationHooks.isNavigationItem(itemEntity.getItem())) {
            itemEntity.discard();
            ci.cancel();
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void discardNavigationItemEntity(Player player, CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (ModNavigationHooks.isNavigationItem(itemEntity.getItem())) {
            itemEntity.discard();
            ci.cancel();
        }
    }
}
