package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;

import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;

public class CommandGenerator {
    public static final String WAYPOINT_COMMAND_WITH_SLASH = "/" + WAYPOINT_COMMAND;

    public static String tpCmd(String dimensionName, String waypointList, String waypointName) {
        return tpCmd(dimensionName, waypointList, waypointName, true);
    }

    public static String tpCmd(String dimensionName, String waypointList, String waypointName, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(TP_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(waypointList).append('"');
        sb.append(" \"").append(waypointName).append('"');
        return sb.toString();
    }

    public static String addCmd(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return addCmd(dimensionName, listName, waypoint, true);
    }

    public static String addCmd(String dimensionName, String listName, SimpleWaypoint waypoint, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(ADD_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(listName).append('"');
        sb.append(' ').append(waypoint.pos().x());
        sb.append(' ').append(waypoint.pos().y());
        sb.append(' ').append(waypoint.pos().z());
        sb.append(" \"").append(waypoint.name()).append('"');
        sb.append(" \"").append(waypoint.initials()).append('"');
        sb.append(' ').append(rgbToNameOrHexCode(waypoint.rgb(), false));
        sb.append(' ').append(waypoint.yaw());
        sb.append(' ').append(waypoint.global());
        return sb.toString();
    }

    public static String editCmd(String dimensionName, String listName, String oldName, SimpleWaypoint waypoint) {
        return editCmd(dimensionName, listName, oldName, waypoint, true);
    }

    public static String editCmd(String dimensionName, String listName, String oldName, SimpleWaypoint waypoint, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(EDIT_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(listName).append('"');
        sb.append(" \"").append(oldName).append('"');
        sb.append(" \"").append(waypoint.name()).append('"');
        sb.append(" \"").append(waypoint.initials()).append('"');
        sb.append(' ').append(waypoint.pos().x());
        sb.append(' ').append(waypoint.pos().y());
        sb.append(' ').append(waypoint.pos().z());
        sb.append(' ').append(rgbToNameOrHexCode(waypoint.rgb(), false));
        sb.append(' ').append(waypoint.yaw());
        sb.append(' ').append(waypoint.global());
        return sb.toString();
    }

    public static String removeCmd(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return removeCmd(dimensionName, listName, waypoint, true);
    }

    public static String removeCmd(String dimensionName, String listName, SimpleWaypoint waypoint, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(REMOVE_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(listName).append('"');
        sb.append(" \"").append(waypoint.name()).append('"');
        return sb.toString();
    }

    public static String addListCmd(String dimensionName, String listName) {
        return addListCmd(dimensionName, listName, true);
    }

    public static String addListCmd(String dimensionName, String listName, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(ADD_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(listName).append('"');
        return sb.toString();
    }

    public static String removeListCmd(String dimensionName, String listName, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(REMOVE_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(" \"").append(listName).append('"');
        return sb.toString();
    }
}
