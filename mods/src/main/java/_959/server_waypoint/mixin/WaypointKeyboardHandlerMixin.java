package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.gui.screens.ClientConfigScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class WaypointKeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    // Screenshot and fullscreen shortcuts can consume a press before Screen.keyPressed.
    //? if >=1.21.9 {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true/*? if >=26 {*/, remap = false/*?}*/)
    private void sw$handleTeleportKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (sw$dispatchTeleportKey(window, action, InputConstants.getKey(event))) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void sw$handleTeleportKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (sw$dispatchTeleportKey(window, action, InputConstants.getKey(key, scanCode))) {
            ci.cancel();
        }
    }
    *///?}

    @Unique
    private boolean sw$dispatchTeleportKey(long window, int action, InputConstants.Key key) {
        if (window == 0 || window != this.minecraft.getWindow()./*? if >=1.21.9 {*/ handle() /*?} else {*//* getWindow() *//*?}*/) {
            return false;
        }
        Screen screen = /*? if >=26.2 {*//* this.minecraft.gui.screen() *//*?} else {*/ this.minecraft.screen /*?}*/;
        if (screen instanceof ClientConfigScreen settings) {
            return settings.captureTeleportKey(key, action);
        }
        return screen instanceof WaypointManagerScreen manager && manager.handleTeleportKey(key, action);
    }
}
