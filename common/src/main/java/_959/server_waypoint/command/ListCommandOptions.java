package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.text.chat.ListView;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * Shared local/remote list grammar; only execution and exact-identity routing differ. Options come
 * in the order search, sort, order, page, limit, view, with search also allowed last and page also
 * allowed after limit and view, which is where the commands the screens build put it.
 *
 * Value arguments keep the tree bounded: expanding sort/order/view into literals duplicates every
 * remaining option subtree for each value, including in Minecraft's per-player command copy.
 */
final class ListCommandOptions<S> {
    @FunctionalInterface interface Factory<S> {
        Command<S> create(WaypointSorting.SortMode mode, boolean reversed, ListView view);
    }

    private static final String SORT_MODE_ARG = "list sort mode";
    private static final String SORT_ORDER_ARG = "list sort order";
    private static final String LIST_VIEW_ARG = "list view";
    private static final List<String> SORT_MODES = Arrays.stream(WaypointSorting.SortMode.values())
            .map(mode -> mode.name().toLowerCase(Locale.ROOT)).toList();
    private static final List<String> SORT_ORDERS = List.of("ascending", "descending");
    private static final List<String> VIEWS = List.of("lists", "tree", "flat");

    private final Factory<S> factory;
    private final Function<String, Command<S>> reserved;
    private final Command<S> command;

    ListCommandOptions(Factory<S> factory, Function<String, Command<S>> reserved) {
        this.factory = factory;
        this.reserved = reserved;
        this.command = this::execute;
    }

    private int execute(CommandContext<S> context) throws CommandSyntaxException {
        String mode = option(context, SORT_MODE_ARG, SORT_MODES);
        String order = option(context, SORT_ORDER_ARG, SORT_ORDERS);
        String view = option(context, LIST_VIEW_ARG, VIEWS);
        WaypointSorting.SortMode sortMode = mode == null ? WaypointSorting.SortMode.DEFAULT
                : WaypointSorting.SortMode.valueOf(mode.toUpperCase(Locale.ROOT));
        if (order != null && sortMode == WaypointSorting.SortMode.DEFAULT) {
            throw invalidOption(context, SORT_ORDER_ARG);
        }
        ListView listView = view == null ? ListView.DEFAULT : ListView.valueOf(view.toUpperCase(Locale.ROOT));
        return factory.create(sortMode, "descending".equals(order), listView).run(context);
    }

    private static <S> String option(CommandContext<S> context, String name, List<String> values)
            throws CommandSyntaxException {
        String value;
        try {
            value = getString(context, name);
        } catch (IllegalArgumentException absent) {
            return null;
        }
        if (!values.contains(value)) throw invalidOption(context, name);
        return value;
    }

    private static <S> CommandSyntaxException invalidOption(CommandContext<S> context, String name) {
        StringReader reader = new StringReader(context.getInput());
        context.getNodes().stream().filter(node -> node.getNode().getName().equals(name)).findFirst()
                .ifPresent(node -> reader.setCursor(node.getRange().getStart()));
        return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
    }

    private RequiredArgumentBuilder<S, String> valueNode(String name, List<String> values) {
        return RequiredArgumentBuilder.<S, String>argument(name, word()).suggests((context, builder) -> {
            // Suggestions may receive the outer context of /execute ... run wp.
            if (name.equals(SORT_ORDER_ARG)
                    && "default".equals(getString(context.getLastChild(), SORT_MODE_ARG))) {
                return builder.buildFuture();
            }
            for (String value : values) {
                if (value.startsWith(builder.getRemainingLowerCase())) builder.suggest(value);
            }
            return builder.buildFuture();
        }).executes(command);
    }

    void configure(ArgumentBuilder<S, ?> targetNode) {
        targetNode.executes(command);
        LiteralArgumentBuilder<S> searchNode = listSearchNode();
        LiteralArgumentBuilder<S> sortNode = listSortNode();
        LiteralArgumentBuilder<S> pageNode = listPageNode();
        LiteralArgumentBuilder<S> limitNode = listLimitNode();
        LiteralArgumentBuilder<S> viewNode = listViewNode();
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
        queryNode.executes(command);
        queryNode.then(listSortNode());
        queryNode.then(listPageNode());
        queryNode.then(listLimitNode());
        queryNode.then(listViewNode());
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode() {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command);
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    /** "page <n>" as the last option, after limit or view. */
    private LiteralArgumentBuilder<S> trailingPageNode() {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command);
        pageNode.then(trailingListSearchNode());
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listViewNode() {
        RequiredArgumentBuilder<S, String> valueNode = valueNode(LIST_VIEW_ARG, VIEWS);
        valueNode.then(trailingListSearchNode());
        valueNode.then(trailingPageNode());
        LiteralArgumentBuilder<S> viewNode = literal(VIEW_COMMAND);
        return viewNode.then(valueNode);
    }

    private LiteralArgumentBuilder<S> listSortNode() {
        RequiredArgumentBuilder<S, String> modeNode = valueNode(SORT_MODE_ARG, SORT_MODES);
        modeNode.then(listOrderNode());
        modeNode.then(trailingListSearchNode());
        modeNode.then(listPageNode());
        modeNode.then(listLimitNode());
        modeNode.then(listViewNode());
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        return sortNode.then(modeNode);
    }

    private LiteralArgumentBuilder<S> listOrderNode() {
        RequiredArgumentBuilder<S, String> directionNode = valueNode(SORT_ORDER_ARG, SORT_ORDERS);
        directionNode.then(trailingListSearchNode());
        directionNode.then(listPageNode());
        directionNode.then(listLimitNode());
        directionNode.then(listViewNode());
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);
        return orderNode.then(directionNode);
    }

    private LiteralArgumentBuilder<S> listPageNode() {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command);
        pageNode.then(trailingListSearchNode());
        pageNode.then(listLimitNode());
        pageNode.then(listViewNode());
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listLimitNode() {
        RequiredArgumentBuilder<S, Integer> limitNode = argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT));
        limitNode.executes(command);
        limitNode.then(trailingListSearchNode());
        limitNode.then(listViewNode());
        limitNode.then(trailingPageNode());
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }
}
