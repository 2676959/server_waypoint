package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.nbt.CompoundTag;
//? if >= 1.20.6 && < 1.21.6 {
import net.minecraft.core.HolderLookup;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
//? if >= 1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DecoratedPotBlockEntity.class)
public abstract class DecoratedPotBlockEntityNavigationMixin {
    //? if >= 1.20.4 {
    @Shadow(/*? if >= 26 {*/remap = false/*?}*/)
    private ItemStack item;

    @Inject(method = "setTheItem", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void blockNavigationItemStorage(ItemStack item, CallbackInfo ci) {
        if (ModNavigationHooks.isNavigationItem(item)) {
            ci.cancel();
        }
    }

    //? if = 1.20.4 {
    @Inject(method = "load", at = @At("TAIL"))
    private void sanitizeLoadedNavigationItem(CompoundTag tag, CallbackInfo ci) {
        removeLoadedNavigationItem();
    }
    //?} elif >= 1.20.6 && < 1.21.6 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void sanitizeLoadedNavigationItem(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo ci
    ) {
        removeLoadedNavigationItem();
    }
    *///?} elif < 26 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void sanitizeLoadedNavigationItem(ValueInput input, CallbackInfo ci) {
        removeLoadedNavigationItem();
    }
    *///?} else {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void sanitizeLoadedNavigationItem(ValueInput input, CallbackInfo ci) {
        removeLoadedNavigationItem();
    }
    *///?}

    @Unique
    private void removeLoadedNavigationItem() {
        if (ModNavigationHooks.isNavigationItem(this.item)) {
            this.item = ItemStack.EMPTY;
        }
    }
    //?}
}
