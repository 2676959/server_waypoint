//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.integrations.VoxelMapWaypointHelper;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.llamalad7.mixinextras.sugar.Local;
import com.mamiyaotaru.voxelmap.gui.overridden.Popup;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.mamiyaotaru.voxelmap.persistent.PersistentMap;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = GuiPersistentMap.class, remap = false)
public abstract class VoxelMapGuiPersistentMapMixin {
    @Unique private static final int SW_ADD = 9590;
    @Unique private static final int SW_EDIT = 9591;
    @Unique private Waypoint sw$popupWaypoint;
    @Shadow private Waypoint selectedWaypoint;
    @Shadow @Final private PersistentMap persistentMap;

    @ModifyArg(method = "createPopup(IIII)V", at = @At(value = "INVOKE",
            //? if >=1.21.11 {
            target = "Lcom/mamiyaotaru/voxelmap/persistent/GuiPersistentMap;createPopup(IIIIILjava/util/ArrayList;)V"), index = 5,
            //?} else {
            /*target = "Lcom/mamiyaotaru/voxelmap/persistent/GuiPersistentMap;createPopup(IIIILjava/util/ArrayList;)V"), index = 4,
            *///?}
            remap = false)
    private ArrayList<Popup.PopupEntry> sw$serverActions(ArrayList<Popup.PopupEntry> entries
            //? if <1.21.11
            /*, @Local(name = "hovered") Waypoint hovered*/
    ) {
        //? if >=1.21.11 {
        this.sw$popupWaypoint = this.selectedWaypoint;
        //?} else {
        /*this.sw$popupWaypoint = hovered;
        *///?}
        if (this.sw$popupWaypoint == null) {
            entries.add(new Popup.PopupEntry(Component.translatable("server_waypoint.map.add_to_server").getString(),
                    SW_ADD, true, true));
        } else if (VoxelMapWaypointHelper.resolveSyncedEditTarget(
                this.sw$popupWaypoint, WaypointClientMod.getInstance()) != null) {
            entries.add(new Popup.PopupEntry(Component.translatable("server_waypoint.map.edit_on_server").getString(),
                    SW_EDIT, true, true));
        }
        return entries;
    }

    @Inject(method = "popupAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void sw$editOnServer(Popup popup, int action, CallbackInfo ci) {
        if (action != SW_EDIT) {
            return;
        }
        if (this.sw$popupWaypoint != null) {
            var target = VoxelMapWaypointHelper.resolveSyncedEditTarget(this.sw$popupWaypoint, WaypointClientMod.getInstance());
            if (target != null) {
                MinecraftClientHelper.setScreen(new WaypointEditScreen((Screen) (Object) this,
                        target.dimensionName(), target.listName(), target.listDisplayName(), target.waypoint()));
            }
        }
        ci.cancel();
    }

    @Inject(method = "popupAction", at = @At(value = "INVOKE",
            target = "Lcom/mamiyaotaru/voxelmap/persistent/PersistentMap;getHeightAt(II)I"),
            cancellable = true, remap = false)
    private void sw$addOnServer(Popup popup, int action, CallbackInfo ci,
            @Local(name = "x") int x, @Local(name = "z") int z) {
        if (action != SW_ADD) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && this.sw$popupWaypoint == null) {
            int y = this.persistentMap.getHeightAt(x, z);
            //? if >=1.21.2 {
            int minY = minecraft.player.level().getMinY();
            //?} else {
            /*int minY = minecraft.player.level().getMinBuildHeight();
            *///?}
            if (y <= minY) {
                y = minecraft.player.blockPosition().getY();
            }
            MinecraftClientHelper.setScreen(new WaypointAddScreen((Screen) (Object) this,
                    WaypointClientMod.getCurrentDimensionName(), "", new WaypointPos(x, y, z)));
        }
        ci.cancel();
    }
}
//?}
