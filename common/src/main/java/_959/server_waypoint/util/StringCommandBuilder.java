package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;

public class StringCommandBuilder {
    public static final String WAYPOINT_COMMAND_WITH_SLASH = "/" + WAYPOINT_COMMAND;

    public record ListTarget(
            boolean allDimensions,
            @Nullable String dimensionName,
            @Nullable String listName
    ) {
    }

    public record ListOptions(
            String filterText,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            int pageNumber,
            int pageLimit,
            boolean groupByLists
    ) {
        public ListOptions(
                String filterText,
                WaypointSorting.SortMode sortMode,
                boolean reversed,
                int pageNumber,
                int pageLimit
        ) {
            this(filterText, sortMode, reversed, pageNumber, pageLimit, true);
        }
    }

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

    public static String navigateStatusCmd() {
        return WAYPOINT_COMMAND_WITH_SLASH + ' ' + NAVIGATE_COMMAND + ' ' + STATUS_COMMAND;
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
        appendExtraInfo(sb, waypoint);
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

    public static String listPageCmd(
            boolean allDimensions,
            String dimensionName,
            String listName,
            String filterText,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            int pageNumber,
            int pageLimit
    ) {
        return listPageCmd(
                allDimensions,
                dimensionName,
                listName,
                filterText,
                sortMode,
                reversed,
                pageNumber,
                pageLimit,
                true
        );
    }

    public static String listPageCmd(
            boolean allDimensions,
            String dimensionName,
            String listName,
            String filterText,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            int pageNumber,
            int pageLimit,
            boolean groupByLists
    ) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(LIST_COMMAND);
        if (allDimensions) {
            command.append(" all");
        } else {
            command.append(' ').append(dimensionName);
            if (listName != null) {
                command.append(' ').append(escapeListName(listName));
            }
        }
        if (!filterText.trim().isEmpty()) {
            command.append(' ').append(SEARCH_COMMAND).append(' ')
                    .append(StringArgumentType.escapeIfRequired(filterText));
        }
        if (sortMode != WaypointSorting.SortMode.DEFAULT) {
            command.append(' ').append(SORT_COMMAND).append(' ')
                    .append(sortMode.name().toLowerCase(Locale.ROOT));
            if (reversed) {
                command.append(' ').append(ORDER_COMMAND).append(" descending");
            }
        }
        command.append(' ').append(PAGE_COMMAND).append(' ').append(pageNumber)
                .append(' ').append(LIMIT_COMMAND).append(' ').append(pageLimit);
        if (!groupByLists) {
            command.append(' ').append(VIEW_COMMAND).append(' ').append(FLAT_VIEW);
        }
        return command.toString();
    }

    public static String listPageCmd(ListTarget target, ListOptions options, int pageNumber) {
        return listPageCmd(
                target.allDimensions(),
                target.dimensionName(),
                target.listName(),
                options.filterText(),
                options.sortMode(),
                options.reversed(),
                pageNumber,
                options.pageLimit(),
                options.groupByLists()
        );
    }

    public static String listSearchCmd(ListTarget target, ListOptions options) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(LIST_COMMAND);
        if (target.allDimensions()) {
            command.append(" all");
        } else {
            command.append(' ').append(target.dimensionName());
            if (target.listName() != null) {
                command.append(' ').append(escapeListName(target.listName()));
            }
        }
        if (options.sortMode() != WaypointSorting.SortMode.DEFAULT) {
            command.append(' ').append(SORT_COMMAND).append(' ')
                    .append(options.sortMode().name().toLowerCase(Locale.ROOT));
            if (options.reversed()) {
                command.append(' ').append(ORDER_COMMAND).append(" descending");
            }
        }
        if (!options.groupByLists()) {
            command.append(' ').append(VIEW_COMMAND).append(' ').append(FLAT_VIEW);
        }
        return command.append(' ').append(SEARCH_COMMAND).append(' ').toString();
    }

    public static String listViewCmd(
            ListTarget target,
            ListOptions options,
            boolean groupByLists
    ) {
        String command = listPageCmd(
                target.allDimensions(),
                target.dimensionName(),
                target.listName(),
                options.filterText(),
                options.sortMode(),
                options.reversed(),
                options.pageNumber(),
                options.pageLimit(),
                groupByLists
        );
        if (groupByLists) {
            return command + ' ' + VIEW_COMMAND + ' ' + TREE_VIEW;
        }
        return command;
    }

    public static String listDimensionCmd(String dimensionName, ListOptions options) {
        return listTargetCmd(new ListTarget(false, dimensionName, null), options);
    }

    public static String listWaypointListCmd(
            String dimensionName,
            String listName,
            ListOptions options
    ) {
        return listTargetCmd(new ListTarget(false, dimensionName, listName), options);
    }

    private static String listTargetCmd(ListTarget target, ListOptions options) {
        ListOptions firstPageOptions = new ListOptions(
                options.filterText(),
                options.sortMode(),
                options.reversed(),
                1,
                options.pageLimit(),
                options.groupByLists()
        );
        return listViewCmd(
                target,
                firstPageOptions,
                options.groupByLists()
        );
    }

    public static String listSortCmd(
            ListTarget target,
            ListOptions options,
            WaypointSorting.SortMode sortMode
    ) {
        return listPageCmd(
                target.allDimensions(),
                target.dimensionName(),
                target.listName(),
                options.filterText(),
                sortMode,
                false,
                1,
                options.pageLimit(),
                options.groupByLists()
        );
    }

    public static String listOrderCmd(ListTarget target, ListOptions options, boolean reversed) {
        return listPageCmd(
                target.allDimensions(),
                target.dimensionName(),
                target.listName(),
                options.filterText(),
                options.sortMode(),
                reversed,
                1,
                options.pageLimit(),
                options.groupByLists()
        );
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

    public static String editListSetCmd(
            String dimensionName,
            String listIdentifier,
            String property,
            String value
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit list " + dimensionName + ' '
                + escapeArgument(listIdentifier) + " set " + property + ' ' + escapeArgument(value);
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

    public static String editWaypointSetCmd(
            String dimensionName,
            String listIdentifier,
            String waypointIdentifier,
            String property,
            String value
    ) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier)
                + " set " + property + ' ' + escapeArgument(value);
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

    private static String suggestionWithValue(String prefix, String value) {
        return value.chars().allMatch(StringCommandBuilder::isAllowedChatCharacter)
                ? prefix + escapeArgument(value)
                : prefix;
    }

    private static boolean isAllowedChatCharacter(int character) {
        return character != '\u00A7' && character >= 32 && character != 127;
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

    private static boolean isListOptionLiteral(String listName) {
        return SEARCH_COMMAND.equals(listName)
                || SORT_COMMAND.equals(listName)
                || ORDER_COMMAND.equals(listName)
                || PAGE_COMMAND.equals(listName)
                || LIMIT_COMMAND.equals(listName)
                || VIEW_COMMAND.equals(listName);
    }
}
