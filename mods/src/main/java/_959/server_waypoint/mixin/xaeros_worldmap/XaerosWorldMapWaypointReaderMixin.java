package _959.server_waypoint.mixin.xaeros_worldmap;

import _959.server_waypoint.access.XaerosWorldMapWaypointAccess;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.integrations.XaerosWorldMapWaypointHelper;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.SyncedWaypointName;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointReader;
import xaero.map.mods.SupportMods;

import java.util.ArrayList;
import java.util.Objects;

import static _959.server_waypoint.common.util.XaerosMapHelper.resolveWorldMapWaypointY;

@Mixin(value = WaypointReader.class, remap = false)
public class XaerosWorldMapWaypointReaderMixin {
    @Inject(method = "getRightClickOptions(Lxaero/map/mods/gui/Waypoint;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;", at = @At(value = "TAIL"), remap = false)
    private void sw$addDropDownOption(final Waypoint element, IRightClickableElement target, CallbackInfoReturnable<ArrayList<RightClickOption>> cir, @Local(name = "rightClickOptions") ArrayList<RightClickOption> rightClickOptions) {
        WaypointReader pointer = (WaypointReader) (Object) this;
        XaerosWorldMapWaypointAccess waypointAccess = (XaerosWorldMapWaypointAccess) element;
        String rawWaypointName = waypointAccess.sw$getRawName();
        String legacySyncedWaypointName = SyncedWaypointName.parseSyncedName(rawWaypointName);
        String syncedListName = SyncedWaypointName.parseSyncedName(waypointAccess.sw$getRawSetName());
        boolean syncedWaypoint = syncedListName != null;
        String syncedWaypointName = legacySyncedWaypointName == null
                ? rawWaypointName
                : legacySyncedWaypointName;
        String dimensionName = XaerosWorldMapWaypointHelper.getWaypointDimensionName(
                SupportMods.xaeroMinimap == null ? null : SupportMods.xaeroMinimap.getWaypointWorld());
        if (dimensionName == null) {
            return;
        }
        var editTarget = syncedWaypoint ? XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                dimensionName, waypointAccess.sw$getRawSetName(), syncedWaypointName, WaypointClientMod.getInstance()
        ) : null;
        rightClickOptions.add(new RightClickOption(syncedWaypoint ? "Edit on server" : "Add to server", rightClickOptions.size(), target) {
                        {
                            Objects.requireNonNull(pointer);
                        }
                        @Override
                        public void onAction(Screen screen) {
                            Minecraft minecraft = Minecraft.getInstance();
                            if (syncedWaypoint) {
                                var currentTarget = XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                                        dimensionName, waypointAccess.sw$getRawSetName(), syncedWaypointName,
                                        WaypointClientMod.getInstance());
                                if (currentTarget == null) {
                                    MinecraftClientHelper.setScreen(minecraft, new AlertScreen(
                                            () -> MinecraftClientHelper.setScreen(minecraft, screen),
                                            Component.translatable("waypoint.edit.screen.title", syncedWaypointName),
                                            Component.translatable("waypoint.edit.error.waypoint_not_found")));
                                    return;
                                }
                                MinecraftClientHelper.setScreen(minecraft, new WaypointEditScreen(
                                        screen,
                                        currentTarget.dimensionName(),
                                        currentTarget.listName(),
                                        currentTarget.listDisplayName(),
                                        currentTarget.waypoint()
                                ));
                                return;
                            }
                            WaypointPos defaultPos = new WaypointPos(
                                    element.getX(),
                                    resolveWorldMapWaypointY(element.isyIncluded(), element.getY(), sw$getFallbackY(minecraft)),
                                    element.getZ()
                            );
                            String listName = sw$getListName(element, waypointAccess);
                            MinecraftClientHelper.setScreen(minecraft, new WaypointAddScreen(
                                    screen,
                                    dimensionName,
                                    listName,
                                    defaultPos
                            ));
                        }
                    }.setActive(!syncedWaypoint || editTarget != null)
        );
    }

    private static int sw$getFallbackY(Minecraft minecraft) {
        //? if >= 1.21.11 {
        BlockPos defaultPos = MinecraftClientHelper.getMainCamera(minecraft).blockPosition();
        //?} else {
        /*BlockPos defaultPos = MinecraftClientHelper.getMainCamera(minecraft).getBlockPosition();
        *///?}
        if (minecraft.getCameraEntity() != null) {
            defaultPos = minecraft.getCameraEntity().blockPosition();
        }
        return defaultPos.getY();
    }

    private static String sw$getListName(Waypoint waypoint, XaerosWorldMapWaypointAccess waypointAccess) {
        String setName = SyncedWaypointName.parseSyncedName(waypointAccess.sw$getRawSetName());
        if (setName == null) {
            setName = waypoint.getSetName();
        }
        return setName == null ? "" : setName;
    }

}
