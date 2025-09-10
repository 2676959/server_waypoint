package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class XaerosMapHelper {
    public static final String XAEROS_SEPARATOR = ":";
    public static final String XAEROS_SHARE_PREFIX = "xaero-waypoint";

    public static boolean isValidXaerosSharingMessage(String[] messageArgs) {
        return messageArgs.length == 10 && XAEROS_SHARE_PREFIX.equals(messageArgs[0]);
    }

    public static Pair<SimpleWaypoint, String> toSimpleWaypoint(String[] args) {
        SimpleWaypoint simpleWaypoint = new SimpleWaypoint(
                args[1],
                args[2],
                new WaypointPos(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5])),
                Integer.parseInt(args[6]),
                Integer.parseInt(args[8]),
                true
        );
        int firstBarIdx = args[9].indexOf('-');
        String xaerosDimensionName = args[9].substring(firstBarIdx + 1);
        String dimensionName = toVanilla(xaerosDimensionName);
        return new Pair<>(simpleWaypoint, dimensionName);
    }

    public static String toVanilla(String xaeroDimString) {
        if (xaeroDimString.endsWith("-waypoints")) {
            xaeroDimString = xaeroDimString.substring(0, xaeroDimString.length() - 10);
        }
        return switch (xaeroDimString) {
            case "overworld" -> MINECRAFT_OVERWORLD;
            case "the-nether" -> MINECRAFT_THE_NETHER;
            case "the-end" -> MINECRAFT_THE_END;
            default -> {
                if (xaeroDimString.length() < 4) {
                    yield "";
                }
                yield xaeroDimString.substring(4).replace("$", ":").replace("%", "/").replaceAll("-", "_");
            }
        };
    }
}
