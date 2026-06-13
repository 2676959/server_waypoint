package _959.server_waypoint.mixin.xaeros_worldmap;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.Objects;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

@Mixin(GuiMap.class)
public abstract class XaeroWorldMapGuiMapMixin {
    @Inject(method = "getRightClickOptions", at = @At(value = "TAIL"), remap = false)
    private void sw$addDropDownOption(CallbackInfoReturnable<ArrayList<RightClickOption>> cir, @Local(name = "options", ordinal = 0) ArrayList<RightClickOption> options) {
        GuiMap pointer = (GuiMap) (Object) this;
        options.add(new RightClickOption("Add waypoint to server", options.size(), pointer) {
                        {
                            Objects.requireNonNull(pointer);
                        }
                        @Override
                        public void onAction(Screen screen) {
                            LOGGER.info("clicked on injected button");
                        }
                    }
        );
    }
}
