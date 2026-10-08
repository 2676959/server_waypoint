package _959.server_waypoint.mixin.xaeros_minimap;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.integrations.XaerosWorldMapWaypointHelper;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.mixin.ButtonOnPressAccessor;
import java.util.ArrayList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.gui.GuiWaypoints;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.world.MinimapWorld;

@Pseudo
@Mixin(value = GuiWaypoints.class, remap = false)
public abstract class XaerosMinimapGuiWaypointsMixin extends Screen {
    protected XaerosMinimapGuiWaypointsMixin(Component title) {
        super(title);
    }

    @Shadow private Button editButton;
    @Shadow private MinimapWorld displayedWorld;
    @Shadow protected abstract ArrayList<Waypoint> getSelectedWaypointsList();

    @Inject(method = "init()V", at = @At("RETURN"), remap = true)
    private void sw$routeServerEdit(CallbackInfo ci) {
        if (this.editButton == null) {
            return;
        }
        ButtonOnPressAccessor button = (ButtonOnPressAccessor) this.editButton;
        Button.OnPress nativeAction = button.sw$getOnPress();
        button.sw$setOnPress(clicked -> {
            ArrayList<Waypoint> selected = this.getSelectedWaypointsList();
            if (selected.size() == 1 && this.displayedWorld != null) {
                String name = selected.get(0).getName();
                String decoded = SyncedWaypointName.parseSyncedName(name);
                var target = XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                        XaerosWorldMapWaypointHelper.getWaypointDimensionName(this.displayedWorld),
                        this.displayedWorld.getCurrentWaypointSetId(), decoded == null ? name : decoded,
                        WaypointClientMod.getInstance());
                if (target != null) {
                    MinecraftClientHelper.setScreen(new WaypointEditScreen((Screen) (Object) this,
                            target.dimensionName(), target.listName(), target.listDisplayName(), target.waypoint()));
                    return;
                }
            }
            nativeAction.onPress(clicked);
        });
    }
}
