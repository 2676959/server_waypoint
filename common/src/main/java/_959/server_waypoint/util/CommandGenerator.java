package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static _959.server_waypoint.util.ColorUtils.rgbToNameOrHexCode;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public class CommandGenerator {
    public static final String WAYPOINT_COMMAND_WITH_SLASH = "/" + WAYPOINT_COMMAND;

    public enum ListScope {
        CURRENT_DIMENSION,
        ALL_DIMENSIONS,
        DIMENSION,
        WAYPOINT_LIST
    }

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
            int pageLimit
    ) {
    }

    @FunctionalInterface
    public interface ListCommandExecutor<S> {
        void execute(
                CommandContext<S> context,
                ListScope scope,
                WaypointSorting.SortMode sortMode,
                boolean reversed,
                @Nullable String fixedListName
        );
    }

    public static <S, D> LiteralArgumentBuilder<S> listCommandNode(
            Supplier<ArgumentType<D>> dimensionArgumentProvider,
            SuggestionProvider<S> waypointListSuggestion,
            ListCommandExecutor<S> executor
    ) {
        LiteralArgumentBuilder<S> listNode = literal(LIST_COMMAND);
        configureListTarget(listNode, ListScope.CURRENT_DIMENSION, executor);

        LiteralArgumentBuilder<S> allNode = literal("all");
        configureListTarget(allNode, ListScope.ALL_DIMENSIONS, executor);
        listNode.then(allNode);

        RequiredArgumentBuilder<S, D> dimensionNode = argument(DIMENSION_ARG, dimensionArgumentProvider.get());
        configureListTarget(dimensionNode, ListScope.DIMENSION, executor);

        RequiredArgumentBuilder<S, String> listNameNode = argument(LIST_NAME_ARG, string());
        listNameNode.suggests(waypointListSuggestion);
        configureListTarget(listNameNode, ListScope.WAYPOINT_LIST, executor);
        dimensionNode.then(listNameNode);
        listNode.then(dimensionNode);
        return listNode;
    }

    private static <S> void configureListTarget(
            ArgumentBuilder<S, ?> targetNode,
            ListScope scope,
            ListCommandExecutor<S> executor
    ) {
        targetNode.executes(listCommand(scope, WaypointSorting.SortMode.DEFAULT, false, executor));
        LiteralArgumentBuilder<S> searchNode = listSearchNode(scope, executor);
        LiteralArgumentBuilder<S> sortNode = listSortNode(scope, executor);
        LiteralArgumentBuilder<S> pageNode = listPageNode(
                scope,
                WaypointSorting.SortMode.DEFAULT,
                false,
                executor
        );
        LiteralArgumentBuilder<S> limitNode = listLimitNode(
                scope,
                WaypointSorting.SortMode.DEFAULT,
                false,
                executor
        );
        if (scope == ListScope.DIMENSION) {
            searchNode.executes(reservedListCommand(SEARCH_COMMAND, executor));
            sortNode.executes(reservedListCommand(SORT_COMMAND, executor));
            pageNode.executes(reservedListCommand(PAGE_COMMAND, executor));
            limitNode.executes(reservedListCommand(LIMIT_COMMAND, executor));
        }
        targetNode.then(searchNode);
        targetNode.then(sortNode);
        targetNode.then(pageNode);
        targetNode.then(limitNode);
    }

    private static <S> LiteralArgumentBuilder<S> listSearchNode(
            ListScope scope,
            ListCommandExecutor<S> executor
    ) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(listCommand(scope, WaypointSorting.SortMode.DEFAULT, false, executor));
        queryNode.then(listSortNode(scope, executor));
        queryNode.then(listPageNode(scope, WaypointSorting.SortMode.DEFAULT, false, executor));
        queryNode.then(listLimitNode(scope, WaypointSorting.SortMode.DEFAULT, false, executor));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private static <S> LiteralArgumentBuilder<S> listSortNode(
            ListScope scope,
            ListCommandExecutor<S> executor
    ) {
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            LiteralArgumentBuilder<S> modeNode = literal(sortMode.name().toLowerCase(Locale.ROOT));
            modeNode.executes(listCommand(scope, sortMode, false, executor));
            if (sortMode != WaypointSorting.SortMode.DEFAULT) {
                modeNode.then(listOrderNode(scope, sortMode, executor));
            }
            modeNode.then(listPageNode(scope, sortMode, false, executor));
            modeNode.then(listLimitNode(scope, sortMode, false, executor));
            sortNode.then(modeNode);
        }
        return sortNode;
    }

    private static <S> LiteralArgumentBuilder<S> listOrderNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            ListCommandExecutor<S> executor
    ) {
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);

        LiteralArgumentBuilder<S> ascendingNode = literal("ascending");
        ascendingNode.executes(listCommand(scope, sortMode, false, executor));
        ascendingNode.then(listPageNode(scope, sortMode, false, executor));
        ascendingNode.then(listLimitNode(scope, sortMode, false, executor));
        orderNode.then(ascendingNode);

        LiteralArgumentBuilder<S> descendingNode = literal("descending");
        descendingNode.executes(listCommand(scope, sortMode, true, executor));
        descendingNode.then(listPageNode(scope, sortMode, true, executor));
        descendingNode.then(listLimitNode(scope, sortMode, true, executor));
        orderNode.then(descendingNode);
        return orderNode;
    }

    private static <S> LiteralArgumentBuilder<S> listPageNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListCommandExecutor<S> executor
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(listCommand(scope, sortMode, reversed, executor));
        pageNode.then(listLimitNode(scope, sortMode, reversed, executor));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private static <S> LiteralArgumentBuilder<S> listLimitNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListCommandExecutor<S> executor
    ) {
        RequiredArgumentBuilder<S, Integer> limitNode = argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT));
        limitNode.executes(listCommand(scope, sortMode, reversed, executor));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }

    private static <S> Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListCommandExecutor<S> executor
    ) {
        return context -> {
            executor.execute(context, scope, sortMode, reversed, null);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static <S> Command<S> reservedListCommand(
            String listName,
            ListCommandExecutor<S> executor
    ) {
        return context -> {
            executor.execute(
                    context,
                    ListScope.WAYPOINT_LIST,
                    WaypointSorting.SortMode.DEFAULT,
                    false,
                    listName
            );
            return Command.SINGLE_SUCCESS;
        };
    }

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
        return command.append(' ').append(PAGE_COMMAND).append(' ').append(pageNumber)
                .append(' ').append(LIMIT_COMMAND).append(' ').append(pageLimit)
                .toString();
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
                options.pageLimit()
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
                options.pageLimit()
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
                options.pageLimit()
        );
    }

    public static String escapeListName(String listName) {
        if (listName.isEmpty()) {
            return "\"\"";
        }
        String escaped = StringArgumentType.escapeIfRequired(listName);
        if (!escaped.equals(listName) || !isListOptionLiteral(listName)) {
            return escaped;
        }
        return "\"" + listName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean isListOptionLiteral(String listName) {
        return SEARCH_COMMAND.equals(listName)
                || SORT_COMMAND.equals(listName)
                || ORDER_COMMAND.equals(listName)
                || PAGE_COMMAND.equals(listName)
                || LIMIT_COMMAND.equals(listName);
    }
}
