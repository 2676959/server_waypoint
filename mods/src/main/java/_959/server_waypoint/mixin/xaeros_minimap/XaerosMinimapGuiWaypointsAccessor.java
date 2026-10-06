package _959.server_waypoint.mixin.xaeros_minimap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.common.gui.GuiWaypoints;
import xaero.hud.minimap.world.MinimapWorld;

@Mixin(value = GuiWaypoints.class, remap = false)
public interface XaerosMinimapGuiWaypointsAccessor {
    @Accessor("displayedWorld")
    MinimapWorld sw$getDisplayedWorld();
}
