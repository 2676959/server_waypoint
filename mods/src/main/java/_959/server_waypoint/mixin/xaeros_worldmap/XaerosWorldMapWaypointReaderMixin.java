package _959.server_waypoint.mixin.xaeros_worldmap;

import _959.server_waypoint.access.XaerosWorldMapWaypointAccess;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.SyncedWaypointName;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
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

import static _959.server_waypoint.common.util.XaerosMapHelper.resolveWorldMapWaypointY;

@Mixin(value = WaypointReader.class, remap = false)
public class XaerosWorldMapWaypointReaderMixin {
    @Inject(method = "getRightClickOptions", at = @At(value = "TAIL"), remap = false)
    private void sw$addDropDownOption(final Waypoint element, IRightClickableElement target, CallbackInfoReturnable<ArrayList<RightClickOption>> cir, @Local(name = "options", ordinal = 0) ArrayList<RightClickOption> options) {
        WaypointReader pointer = (WaypointReader) (Object) this;
        XaerosWorldMapWaypointAccess waypointAccess = (XaerosWorldMapWaypointAccess) element;
        String syncedWaypointName = SyncedWaypointName.parseSyncedName(waypointAccess.sw$getRawName());
        boolean syncedWaypoint = syncedWaypointName != null;
        options.add(new RightClickOption(syncedWaypoint ? "Edit on server" : "Add to server", options.size(), target) {
                        {
                            Objects.requireNonNull(pointer);
                        }
                        @Override
                        public void onAction(Screen screen) {
                            Minecraft minecraft = Minecraft.getInstance();
                            WaypointPos defaultPos = new WaypointPos(
                                    element.getX(),
                                    resolveWorldMapWaypointY(element.isyIncluded(), element.getY(), sw$getFallbackY(minecraft)),
                                    element.getZ()
                            );
                            String dimensionName = sw$getCurrentDimensionName();
                            String listName = sw$getListName(element, waypointAccess);
                            if (syncedWaypoint) {
                                minecraft.setScreen(new WaypointEditScreen(
                                        screen,
                                        dimensionName,
                                        listName,
                                        sw$toSimpleWaypoint(element, syncedWaypointName, defaultPos)
                                ));
                                return;
                            }
                            minecraft.setScreen(new WaypointAddScreen(
                                    screen,
                                    dimensionName,
                                    listName,
                                    defaultPos
                            ));
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

    private static String sw$getCurrentDimensionName() {
        String currentDimensionName = WaypointClientMod.getCurrentDimensionName();
        return currentDimensionName == null ? "" : currentDimensionName;
    }

    private static String sw$getListName(Waypoint waypoint, XaerosWorldMapWaypointAccess waypointAccess) {
        String setName = SyncedWaypointName.parseSyncedName(waypointAccess.sw$getRawSetName());
        if (setName == null) {
            setName = waypoint.getSetName();
        }
        return setName == null ? "" : setName;
    }

    private static SimpleWaypoint sw$toSimpleWaypoint(Waypoint waypoint, String waypointName, WaypointPos pos) {
        return new SimpleWaypoint(
                waypointName,
                waypoint.getSymbol(),
                pos,
                waypoint.getColor(),
                waypoint.getYaw(),
                waypoint.isGlobal()
        );
    }
}
