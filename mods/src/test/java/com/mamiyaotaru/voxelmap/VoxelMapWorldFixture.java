//? if voxelmap {
package com.mamiyaotaru.voxelmap;

import _959.server_waypoint.mixin.voxelmap.VoxelMapWorldAccessor;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import net.minecraft.client.multiplayer.ClientLevel;

/** Same-package fixture for VoxelMap's package-private constructor; allocated without its HUD. */
public class VoxelMapWorldFixture extends VoxelMap implements VoxelMapWorldAccessor {
    public ClientLevel loadedWorld;
    public WaypointManager waypointManager;
    public DimensionManager dimensionManager;

    @Override public ClientLevel sw$getWorld() { return loadedWorld; }
    @Override public WaypointManager getWaypointManager() { return waypointManager; }
    @Override public DimensionManager getDimensionManager() { return dimensionManager; }
}
//?}
