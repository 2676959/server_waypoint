package _959.server_waypoint.common.util;

import net.minecraft.util.math.BlockPos;

public class BlockPosConverter {
    public static BlockPos netherToOverWorld(BlockPos pos) {
        return new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8);
    }

    public static BlockPos overWorldToNether(BlockPos pos) {
        return new BlockPos(Math.floorDiv(pos.getX(), 8), pos.getY(), Math.floorDiv(pos.getZ(), 8));
    }
}
