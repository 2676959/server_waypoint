package _959.server_waypoint.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TeleportCommandGenerator {
    public static String tpCmd(RegistryKey<World> dimKey, BlockPos pos, int yaw) {
        return "/execute in %s run tp @s %d %d %d %d 0".formatted(dimKey.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), yaw);
    }
}
