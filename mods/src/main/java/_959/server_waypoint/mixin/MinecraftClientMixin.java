package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static _959.server_waypoint.common.client.WaypointClientMod.onDimensionChange;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "updateLevelInEngines", at = @At(value = "HEAD"))
    public void setWorld(ClientLevel world, CallbackInfo ci) {
        if (world == null) {
            return;
        }
        String worldName = world.dimension().identifier().toString();
        WaypointManagerScreen.resetWidgetStates();
        onDimensionChange(worldName);
    }
}
