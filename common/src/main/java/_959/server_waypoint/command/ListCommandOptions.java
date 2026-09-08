package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import java.util.Locale;
import java.util.function.Function;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/** Shared local/remote list grammar; only execution and exact-identity routing differ. */
final class ListCommandOptions<S> {
    @FunctionalInterface interface Factory<S> {
        Command<S> create(WaypointSorting.SortMode mode, boolean reversed, boolean grouped);
    }
    private final Factory<S> factory;
    private final Function<String, Command<S>> reserved;
    ListCommandOptions(Factory<S> factory, Function<String, Command<S>> reserved) {
        this.factory = factory; this.reserved = reserved;
    }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed) { return command(mode, reversed, true); }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed, boolean grouped) {
        return factory.create(mode, reversed, grouped);
    }
    void configure(ArgumentBuilder<S, ?> targetNode) {
        targetNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = listSearchNode();
        LiteralArgumentBuilder<S> sortNode = listSortNode();
        LiteralArgumentBuilder<S> pageNode = listPageNode(
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        LiteralArgumentBuilder<S> limitNode = listLimitNode(
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        LiteralArgumentBuilder<S> viewNode = listViewNode(
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        if (reserved != null) {
            searchNode.executes(reserved.apply(SEARCH_COMMAND));
            sortNode.executes(reserved.apply(SORT_COMMAND));
            pageNode.executes(reserved.apply(PAGE_COMMAND));
            limitNode.executes(reserved.apply(LIMIT_COMMAND));
            viewNode.executes(reserved.apply(VIEW_COMMAND));
        }
        targetNode.then(searchNode);
        targetNode.then(sortNode);
        targetNode.then(pageNode);
        targetNode.then(limitNode);
        targetNode.then(viewNode);
    }

    private LiteralArgumentBuilder<S> listSearchNode() {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listSortNode());
        queryNode.then(listPageNode(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listLimitNode(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listViewNode(WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        return trailingListSearchNode(sortMode, reversed, true);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            boolean groupByLists
    ) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command(sortMode, reversed, groupByLists));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> listViewNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        LiteralArgumentBuilder<S> treeNode = literal(TREE_VIEW);
        treeNode.executes(command(sortMode, reversed, true));
        treeNode.then(trailingListSearchNode(sortMode, reversed, true));

        LiteralArgumentBuilder<S> flatNode = literal(FLAT_VIEW);
        flatNode.executes(command(sortMode, reversed, false));
        flatNode.then(trailingListSearchNode(sortMode, reversed, false));

        LiteralArgumentBuilder<S> viewNode = literal(VIEW_COMMAND);
        viewNode.then(treeNode);
        viewNode.then(flatNode);
        return viewNode;
    }

    private LiteralArgumentBuilder<S> listSortNode() {
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            LiteralArgumentBuilder<S> modeNode = literal(sortMode.name().toLowerCase(Locale.ROOT));
            modeNode.executes(command(sortMode, false));
            if (sortMode != WaypointSorting.SortMode.DEFAULT) {
                modeNode.then(listOrderNode(sortMode));
            }
            modeNode.then(trailingListSearchNode(sortMode, false));
            modeNode.then(listPageNode(sortMode, false));
            modeNode.then(listLimitNode(sortMode, false));
            modeNode.then(listViewNode(sortMode, false));
            sortNode.then(modeNode);
        }
        return sortNode;
    }

    private LiteralArgumentBuilder<S> listOrderNode(
            WaypointSorting.SortMode sortMode
    ) {
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);

        LiteralArgumentBuilder<S> ascendingNode = literal("ascending");
        ascendingNode.executes(command(sortMode, false));
        ascendingNode.then(trailingListSearchNode(sortMode, false));
        ascendingNode.then(listPageNode(sortMode, false));
        ascendingNode.then(listLimitNode(sortMode, false));
        ascendingNode.then(listViewNode(sortMode, false));
        orderNode.then(ascendingNode);

        LiteralArgumentBuilder<S> descendingNode = literal("descending");
        descendingNode.executes(command(sortMode, true));
        descendingNode.then(trailingListSearchNode(sortMode, true));
        descendingNode.then(listPageNode(sortMode, true));
        descendingNode.then(listLimitNode(sortMode, true));
        descendingNode.then(listViewNode(sortMode, true));
        orderNode.then(descendingNode);
        return orderNode;
    }

    private LiteralArgumentBuilder<S> listPageNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command(sortMode, reversed));
        pageNode.then(trailingListSearchNode(sortMode, reversed));
        pageNode.then(listLimitNode(sortMode, reversed));
        pageNode.then(listViewNode(sortMode, reversed));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listLimitNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> limitNode = argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT));
        limitNode.executes(command(sortMode, reversed));
        limitNode.then(trailingListSearchNode(sortMode, reversed));
        limitNode.then(listViewNode(sortMode, reversed));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }

}
