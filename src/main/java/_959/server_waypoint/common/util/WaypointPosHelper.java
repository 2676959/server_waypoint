package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.util.math.BlockPos;

@Deprecated
public class WaypointPosHelper {
   public static BlockPos toBlockPos(WaypointPos pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }

   public static WaypointPos fromBlockPos(BlockPos pos) { return new WaypointPos(pos.getX(), pos.getY(), pos.getZ()); }
}
