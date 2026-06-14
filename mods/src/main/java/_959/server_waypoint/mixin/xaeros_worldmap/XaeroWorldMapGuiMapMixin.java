package _959.server_waypoint.mixin.xaeros_worldmap;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.Objects;

import static _959.server_waypoint.util.XaerosMapHelper.resolveWorldMapRightClickY;

@Mixin(value = GuiMap.class, remap = false)
public abstract class XaeroWorldMapGuiMapMixin {
    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickY;

    @Shadow
    private int rightClickZ;

    @Shadow
    private ResourceKey<Level> rightClickDim;

    @Inject(method = "getRightClickOptions", at = @At(value = "TAIL"), remap = false)
    private void sw$addDropDownOption(CallbackInfoReturnable<ArrayList<RightClickOption>> cir, @Local(name = "options", ordinal = 0) ArrayList<RightClickOption> options) {
        GuiMap pointer = (GuiMap) (Object) this;
        options.add(new RightClickOption("Add waypoint to server", options.size(), pointer) {
                        {
                            Objects.requireNonNull(pointer);
                        }
                        @Override
                        public void onAction(Screen screen) {
                            Minecraft minecraft = Minecraft.getInstance();
                            WaypointPos defaultPos = new WaypointPos(
                                    rightClickX,
                                    resolveWorldMapRightClickY(rightClickY, sw$getFallbackY(minecraft)),
                                    rightClickZ
                            );
                            minecraft.setScreen(new WaypointAddScreen(screen, sw$getDimensionName(rightClickDim), "", defaultPos));
                        }
                    }
        );
    }

    private static int sw$getFallbackY(Minecraft minecraft) {
        //? if >= 1.21.11 {
        BlockPos defaultPos = minecraft.gameRenderer.getMainCamera().blockPosition();
        //?} else {
        /*BlockPos defaultPos = minecraft.gameRenderer.getMainCamera().getBlockPosition();
        *///?}
        if (minecraft.getCameraEntity() != null) {
            defaultPos = minecraft.getCameraEntity().blockPosition();
        }
        return defaultPos.getY();
    }

    private static String sw$getDimensionName(ResourceKey<Level> rightClickDim) {
        if (rightClickDim == null) {
            String currentDimensionName = WaypointClientMod.getCurrentDimensionName();
            return currentDimensionName == null ? "" : currentDimensionName;
        }
        //? if >= 1.21.11 {
        return rightClickDim.identifier().toString();
        //?} else {
        /*return rightClickDim.location().toString();
        *///?}
    }
}
