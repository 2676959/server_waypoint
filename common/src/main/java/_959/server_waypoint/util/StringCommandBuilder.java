package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.upload.UploadConflictPolicy;
import _959.server_waypoint.core.network.upload.UploadScope;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;

public class StringCommandBuilder {
    public static final String WAYPOINT_COMMAND_WITH_SLASH = "/" + WAYPOINT_COMMAND;

    public static String tpCmd(String dimensionName, String waypointList, String waypointName) {
        return tpCmd(dimensionName, waypointList, waypointName, true);
    }

    public static String navigateCmd(String dimensionName, String listName, String waypointName) {
        return selectorCmd(NAVIGATE_COMMAND, dimensionName, listName, waypointName);
    }

    public static String navigateWithMethodsCmd(
            String dimensionName,
            String listName,
            String waypointName,
            String methods
    ) {
        return navigateCmd(dimensionName, listName, waypointName) + ' ' + methods;
    }

    public static String navigateUseCmd(String method) {
        return WAYPOINT_COMMAND_WITH_SLASH + ' ' + NAVIGATE_COMMAND + ' ' + USE_COMMAND + ' ' + method;
    }

    public static String navigateDisableCmd() {
        return WAYPOINT_COMMAND_WITH_SLASH + ' ' + NAVIGATE_COMMAND + ' ' + DISABLE_COMMAND;
    }

    public static String navigateDisableCmd(String method) {
        return navigateDisableCmd() + ' ' + method;
    }

    /** /wp upload <source> [force local [delete]] [<dimension> [<list> [<waypoint>]]] for this request's scope. */
    public static String uploadCmd(UploadScope scope, UploadRequestBuffer request, UploadConflictPolicy conflictPolicy,
                                   boolean deleteMissing) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(UPLOAD_COMMAND)
                .append(' ').append(request.target().name().toLowerCase(Locale.ROOT));
        if (conflictPolicy == UploadConflictPolicy.LOCAL) {
            command.append(" force local");
            if (deleteMissing) {
                command.append(" delete");
            }
        }
        if (scope == UploadScope.WORLD) {
            return command.toString();
        }
        command.append(' ').append(request.dimensionNames().get(0));
        if (scope == UploadScope.DIMENSION) {
            return command.toString();
        }
        command.append(' ').append(escapeArgument(request.listName()));
        if (scope == UploadScope.LIST) {
            return command.toString();
        }
        return command.append(' ').append(escapeArgument(request.waypointName())).toString();
    }

    private static String selectorCmd(
            String command,
            String dimensionName,
            String listName,
            String waypointName
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + ' ' + command
                + ' ' + dimensionName
                + ' ' + escapeArgument(listName)
                + ' ' + escapeArgument(waypointName);
    }

    public static String tpCmd(String dimensionName, String waypointList, String waypointName, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(TP_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(' ').append(escapeArgument(waypointList));
        sb.append(' ').append(escapeArgument(waypointName));
        return sb.toString();
    }

    public static String addCmd(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return addCmd(dimensionName, listName, waypoint, true);
    }

    public static String addCmd(String dimensionName, String listName, SimpleWaypoint waypoint, boolean withSlash) {
        waypoint = new SimpleWaypoint(waypoint);
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(ADD_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(' ').append(escapeArgument(listName));
        sb.append(' ').append(waypoint.pos().x());
        sb.append(' ').append(waypoint.pos().y());
        sb.append(' ').append(waypoint.pos().z());
        sb.append(' ').append(escapeArgument(waypoint.name()));
        sb.append(' ').append(escapeArgument(waypoint.initials()));
        sb.append(' ').append(rgbToNameOrHexCode(waypoint.rgb(), false));
        sb.append(' ').append(waypoint.yaw());
        sb.append(' ').append(waypoint.global());
        if (waypoint.icon() == null) {
            appendExtraInfo(sb, waypoint);
        } else {
            sb.append(' ').append(escapeArgument(String.join(", ", waypoint.keywords())));
            sb.append(' ').append(escapeArgument(waypoint.description()));
            sb.append(" icon ").append(waypoint.icon());
        }
        return sb.toString();
    }

    public static String editCmd(String dimensionName, String listName, String oldName, SimpleWaypoint waypoint) {
        return editCmd(dimensionName, listName, oldName, waypoint, true);
    }

    public static String editCmd(String dimensionName, String listName, String oldName, SimpleWaypoint waypoint, boolean withSlash) {
        waypoint = new SimpleWaypoint(waypoint);
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(EDIT_COMMAND).append(" waypoint");
        sb.append(' ').append(dimensionName);
        sb.append(' ').append(escapeArgument(listName));
        sb.append(' ').append(escapeArgument(oldName));
        sb.append(" set identifier ").append(escapeArgument(waypoint.name()));
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
        sb.append(' ').append(escapeArgument(listName));
        sb.append(' ').append(escapeArgument(waypoint.name()));
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
        sb.append(' ').append(escapeArgument(listName));
        return sb.toString();
    }

    public static String removeListCmd(String dimensionName, String listName, boolean withSlash) {
        StringBuilder sb = new StringBuilder();
        sb.append(withSlash ? WAYPOINT_COMMAND_WITH_SLASH : WAYPOINT_COMMAND);
        sb.append(' ').append(REMOVE_COMMAND);
        sb.append(' ').append(dimensionName);
        sb.append(' ').append(escapeArgument(listName));
        return sb.toString();
    }

    private static void appendExtraInfo(StringBuilder command, SimpleWaypoint waypoint) {
        if (waypoint.keywords().isEmpty() && waypoint.description().isEmpty()) {
            return;
        }
        command.append(' ').append(escapeArgument(String.join(", ", waypoint.keywords())));
        if (!waypoint.description().isEmpty()) {
            command.append(' ').append(escapeArgument(waypoint.description()));
        }
    }

    public static String escapeListName(String listName) {
        String escaped = escapeArgument(listName);
        if (!escaped.equals(listName) || !isListOptionLiteral(listName)) {
            return escaped;
        }
        return "\"" + listName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String escapeArgument(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        return StringArgumentType.escapeIfRequired(value);
    }

    public static String detailsListCmd(String dimensionName, String listIdentifier) {
        return WAYPOINT_COMMAND_WITH_SLASH + " details list " + dimensionName + ' '
                + escapeArgument(listIdentifier);
    }

    public static String detailsWaypointCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " details waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier);
    }

    public static String editListSetSuggestionCmd(
            String dimensionName,
            String listIdentifier,
            String property,
            String value
    ) {
        String prefix = WAYPOINT_COMMAND_WITH_SLASH + " edit list " + dimensionName + ' '
                + escapeArgument(listIdentifier) + " set " + property + ' ';
        return suggestionWithValue(prefix, value);
    }

    public static String editListClearCmd(
            String dimensionName,
            String listIdentifier,
            String property
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit list " + dimensionName + ' '
                + escapeArgument(listIdentifier) + " clear " + property;
    }

    public static String editWaypointSetSuggestionCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier,
            String property,
            String value
    ) {
        String prefix = WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier)
                + " set " + property + ' ';
        return suggestionWithValue(prefix, value);
    }

    public static String editWaypointIconSuggestionCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier,
            @Nullable NamespacedId icon
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier)
                + " set icon " + (icon == null ? "" : icon.toString());
    }

    private static String suggestionWithValue(String prefix, String value) {
        return value.chars().allMatch(StringCommandBuilder::isAllowedChatCharacter)
                ? prefix + escapeArgument(value)
                : prefix;
    }

    private static boolean isAllowedChatCharacter(int character) {
        return character != '\u00A7' && character >= 32 && character != 127;
    }

    /** /wp edit waypoint <dimension> <list> <waypoint> followed by the rest of the command. */
    public static String editWaypointCmd(String dimensionName, String listIdentifier, String waypointIdentifier, String tail) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier) + ' ' + tail;
    }

    public static String editWaypointClearCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier,
            String property
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier)
                + " clear " + property;
    }

    public static String editWaypointPositionCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier,
            SimpleWaypoint waypoint
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier)
                + " set position " + waypoint.x() + ' ' + waypoint.y() + ' ' + waypoint.z();
    }

    public static String restoreCmd(String token) {
        return WAYPOINT_COMMAND_WITH_SLASH + " restore " + escapeArgument(token);
    }

    /** /wp download <dimension> [<list> [<waypoint>]] */
    public static String downloadCmd(String dimensionName, @Nullable String listName, @Nullable String waypointName) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(DOWNLOAD_COMMAND)
                .append(' ').append(dimensionName);
        if (listName != null) {
            command.append(' ').append(escapeArgument(listName));
            if (waypointName != null) {
                command.append(' ').append(escapeArgument(waypointName));
            }
        }
        return command.toString();
    }

    private static boolean isListOptionLiteral(String listName) {
        return SEARCH_COMMAND.equals(listName)
                || SORT_COMMAND.equals(listName)
                || ORDER_COMMAND.equals(listName)
                || PAGE_COMMAND.equals(listName)
                || LIMIT_COMMAND.equals(listName)
                || VIEW_COMMAND.equals(listName);
    }
}
