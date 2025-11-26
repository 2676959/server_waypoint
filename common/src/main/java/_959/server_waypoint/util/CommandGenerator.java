package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;

public class CommandGenerator {
    public static final String ROOT_WAYPOINT_COMMAND = "/" + WAYPOINT_COMMAND;

    public static String tpCmd(String dimensionName, String waypointList, String waypointName) {
        return ROOT_WAYPOINT_COMMAND + " " + TP_COMMAND + " " +
        "%s \"%s\" \"%s\"".formatted(dimensionName, waypointList, waypointName);
    }

    public static String addCmd(String dimString, String listName, SimpleWaypoint waypoint) {
        return ROOT_WAYPOINT_COMMAND + " " + ADD_COMMAND + " " +
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
        return ROOT_WAYPOINT_COMMAND + " " + EDIT_COMMAND + " " +
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
        return ROOT_WAYPOINT_COMMAND + " " + REMOVE_COMMAND + " " +
                "%s \"%s\" \"%s\"".formatted(
                        dimString,
                        listName,
                        waypoint.name()
                );
    }

    public static String addListCmd(String dimString, String listName) {
        return ROOT_WAYPOINT_COMMAND + " " + ADD_COMMAND + " " +
                "%s \"%s\"".formatted(
                        dimString,
                        listName
                );
    }
}
