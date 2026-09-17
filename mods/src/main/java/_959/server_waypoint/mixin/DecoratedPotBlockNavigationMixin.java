package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockNavigationMixin {
    //? if = 1.20.4 {
    /*@Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void blockNavigationItemInsertion(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
    *///?} elif >= 1.20.6 && < 1.21.2 && < 26 {
    /*@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void blockNavigationItemInsertion(
            ItemStack item,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<net.minecraft.world.ItemInteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(item)) {
            cir.setReturnValue(net.minecraft.world.ItemInteractionResult.FAIL);
        }
    }
    *///?} elif >= 1.21.2 && < 26 {
    /*@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void blockNavigationItemInsertion(
            ItemStack item,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(item)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
    *///?} elif >= 26 {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockNavigationItemInsertion(
            ItemStack item,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (ModNavigationHooks.isNavigationItem(item)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
    //?}
}
