package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static _959.server_waypoint.util.TextHelper.colorIndexToName;

public class CommandGenerator {
    public static final String WAYPOINT_COMMAND = "/wp";
    public static final String ADD_ARGUMENT = "add";
    public static final String EDIT_ARGUMENT = "edit";
    public static final String REMOVE_ARGUMENT = "remove";
    public static String tpCmd(RegistryKey<World> dimKey, BlockPos pos, int yaw) {
        return "/execute in %s run tp @s %d %d %d %d 0".formatted(dimKey.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), yaw);
    }

    public static String addCmd(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + ADD_ARGUMENT + " " +
                "%s %d %d %d %s %s %s %s %d %b"
                        .formatted(
                                dimKey.getValue().toString(),
                                waypoint.pos().getX(),
                                waypoint.pos().getY(),
                                waypoint.pos().getZ(),
                                listName,
                                waypoint.name(),
                                waypoint.initials(),
                                colorIndexToName(waypoint.colorIdx()),
                                waypoint.yaw(),
                                waypoint.global()
                );
    }

    public static String editCmd(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + EDIT_ARGUMENT + " " +
                "%s %s %s %s %d %d %d %s %d %b"
                        .formatted(
                                dimKey.getValue().toString(),
                                listName,
                                waypoint.name(),
                                waypoint.initials(),
                                waypoint.pos().getX(),
                                waypoint.pos().getY(),
                                waypoint.pos().getZ(),
                                colorIndexToName(waypoint.colorIdx()),
                                waypoint.yaw(),
                                waypoint.global()
                        );
    }

    public static String removeCmd(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + REMOVE_ARGUMENT + " " +
                "%s %s %s".formatted(
                        dimKey.getValue().toString(),
                        listName,
                        waypoint.name()
                );
    }
}
