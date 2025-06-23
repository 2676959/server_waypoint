package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CommandGenerator {
    public static String tpCmd(RegistryKey<World> dimKey, BlockPos pos, int yaw) {
        return "/execute in %s run tp @s %d %d %d %d 0".formatted(dimKey.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), yaw);
    }

    public static String editCmd(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        return "/wp edit %s %s %s %s %d %d %d %s %d %b"
                .formatted(dimKey.getValue().toString(),
                        listName,
                        waypoint.name(),
                        waypoint.initials(),
                        waypoint.pos().getX(),
                        waypoint.pos().getY(),
                        waypoint.pos().getZ(),
                        Formatting.byColorIndex(waypoint.colorIdx()).asString(),
                        waypoint.yaw(),
                        waypoint.global()
                );

    }
}
