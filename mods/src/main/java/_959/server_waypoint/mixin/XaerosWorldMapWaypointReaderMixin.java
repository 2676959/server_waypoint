package _959.server_waypoint.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointReader;

import java.util.ArrayList;
import java.util.Objects;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

@Mixin(WaypointReader.class)
public class XaerosWorldMapWaypointReaderMixin {
    @Inject(method = "getRightClickOptions", at = @At(value = "TAIL"), remap = false)
    private void sw$addDropDownOption(final Waypoint element, IRightClickableElement target, CallbackInfoReturnable<ArrayList<RightClickOption>> cir, @Local(name = "options", ordinal = 0) ArrayList<RightClickOption> options) {
        WaypointReader pointer = (WaypointReader) (Object) this;
        options.add(new RightClickOption("Send to server", options.size(), target) {
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
