package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.text.chat.ListView;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * Shared local/remote list grammar; only execution and exact-identity routing differ. Options come
 * in the order search, sort, order, page, limit, view, with search also allowed last and page also
 * allowed after limit and view, which is where the commands the screens build put it.
 */
final class ListCommandOptions<S> {
    @FunctionalInterface interface Factory<S> {
        Command<S> create(WaypointSorting.SortMode mode, boolean reversed, ListView view);
    }
    private static final List<ListView> VIEWS = List.of(ListView.LISTS, ListView.TREE, ListView.FLAT);
    private final Factory<S> factory;
    private final Function<String, Command<S>> reserved;
    ListCommandOptions(Factory<S> factory, Function<String, Command<S>> reserved) {
        this.factory = factory; this.reserved = reserved;
    }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed) { return command(mode, reversed, ListView.DEFAULT); }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed, ListView view) {
        return factory.create(mode, reversed, view);
    }
    void configure(ArgumentBuilder<S, ?> targetNode) {
        targetNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = listSearchNode();
        LiteralArgumentBuilder<S> sortNode = listSortNode();
        LiteralArgumentBuilder<S> pageNode = listPageNode(WaypointSorting.SortMode.DEFAULT, false);
        LiteralArgumentBuilder<S> limitNode = listLimitNode(WaypointSorting.SortMode.DEFAULT, false);
        LiteralArgumentBuilder<S> viewNode = listViewNode(WaypointSorting.SortMode.DEFAULT, false);
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
            boolean reversed,
            ListView view
    ) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command(sortMode, reversed, view));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    /** "page <n>" as the last option, after limit or view. */
    private LiteralArgumentBuilder<S> trailingPageNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command(sortMode, reversed, view));
        pageNode.then(trailingListSearchNode(sortMode, reversed, view));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listViewNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        LiteralArgumentBuilder<S> viewNode = literal(VIEW_COMMAND);
        for (ListView view : VIEWS) {
            LiteralArgumentBuilder<S> valueNode = literal(view.id());
            valueNode.executes(command(sortMode, reversed, view));
            valueNode.then(trailingListSearchNode(sortMode, reversed, view));
            valueNode.then(trailingPageNode(sortMode, reversed, view));
            viewNode.then(valueNode);
        }
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
            modeNode.then(trailingListSearchNode(sortMode, false, ListView.DEFAULT));
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
        for (boolean descending : new boolean[]{false, true}) {
            LiteralArgumentBuilder<S> directionNode = literal(descending ? "descending" : "ascending");
            directionNode.executes(command(sortMode, descending));
            directionNode.then(trailingListSearchNode(sortMode, descending, ListView.DEFAULT));
            directionNode.then(listPageNode(sortMode, descending));
            directionNode.then(listLimitNode(sortMode, descending));
            directionNode.then(listViewNode(sortMode, descending));
            orderNode.then(directionNode);
        }
        return orderNode;
    }

    private LiteralArgumentBuilder<S> listPageNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command(sortMode, reversed));
        pageNode.then(trailingListSearchNode(sortMode, reversed, ListView.DEFAULT));
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
        limitNode.then(trailingListSearchNode(sortMode, reversed, ListView.DEFAULT));
        limitNode.then(listViewNode(sortMode, reversed));
        limitNode.then(trailingPageNode(sortMode, reversed, ListView.DEFAULT));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }
}
