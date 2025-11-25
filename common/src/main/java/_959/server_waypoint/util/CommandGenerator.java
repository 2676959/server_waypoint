package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;

public class CommandGenerator {
    public static final String WAYPOINT_COMMAND = "/wp";
    public static final String ADD_ARGUMENT = "add";
    public static final String EDIT_ARGUMENT = "edit";
    public static final String REMOVE_ARGUMENT = "remove";
    public static final String TP_ARGUMENT = "tp";

    public static String tpCmd(String dimensionName, String waypointList, String waypointName) {
        return WAYPOINT_COMMAND + " " + TP_ARGUMENT + " " +
        "%s \"%s\" \"%s\"".formatted(dimensionName, waypointList, waypointName);
    }

    public static String addCmd(String dimString, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + ADD_ARGUMENT + " " +
                "%s \"%s\" %d %d %d \"%s\" \"%s\" %s %d %b"
                        .formatted(
                                dimString,
                                listName,
                                waypoint.pos().x(),
                                waypoint.pos().y(),
                                waypoint.pos().z(),
                                waypoint.name(),
                                waypoint.initials(),
                                rgbToNameOrHexCode(waypoint.rgb(), false),
                                waypoint.yaw(),
                                waypoint.global()
                );
    }

    public static String editCmd(String dimString, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + EDIT_ARGUMENT + " " +
                "%s \"%s\" \"%s\" \"%s\" %d %d %d %s %d %b"
                        .formatted(
                                dimString,
                                listName,
                                waypoint.name(),
                                waypoint.initials(),
                                waypoint.pos().x(),
                                waypoint.pos().y(),
                                waypoint.pos().z(),
                                rgbToNameOrHexCode(waypoint.rgb(), false),
                                waypoint.yaw(),
                                waypoint.global()
                        );
    }

    public static String removeCmd(String dimString, String listName, SimpleWaypoint waypoint) {
        return WAYPOINT_COMMAND + " " + REMOVE_ARGUMENT + " " +
                "%s \"%s\" \"%s\"".formatted(
                        dimString,
                        listName,
                        waypoint.name()
                );
    }

    public static String addListCmd(String dimString, String listName) {
        return WAYPOINT_COMMAND + " " + ADD_ARGUMENT + " " +
                "%s \"%s\"".formatted(
                        dimString,
                        listName
                );
    }
}
