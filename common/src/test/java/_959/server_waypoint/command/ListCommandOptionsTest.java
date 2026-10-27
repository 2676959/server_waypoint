package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.text.chat.ListView;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static org.junit.jupiter.api.Assertions.*;

class ListCommandOptionsTest {
    private record Selection(SortMode mode, boolean reversed, ListView view, String search, int page, int limit) {
    }

    private Selection selected;

    private CommandDispatcher<String> dispatcher() {
        var root = com.mojang.brigadier.builder.LiteralArgumentBuilder.<String>literal("list");
        new ListCommandOptions<String>((mode, reversed, view) -> context -> {
            selected = new Selection(mode, reversed, view, optionalString(context, CoreWaypointCommand.SEARCH_QUERY_ARG),
                    optionalInteger(context, CoreWaypointCommand.PAGE_NUMBER_ARG, 1),
                    optionalInteger(context, CoreWaypointCommand.PAGE_LIMIT_ARG, 5));
            return 1;
        }, null).configure(root);
        var dispatcher = new CommandDispatcher<String>();
        dispatcher.register(root);
        return dispatcher;
    }

    @Test
    void optionTreeStaysSmallBeforeAndAfterVanillaStyleCopy() {
        var root = dispatcher().getRoot();
        int nodes = count(root);
        int copiedNodes = count(copy(root));
        System.out.printf("List option nodes: server=%d, copied=%d%n", nodes, copiedNodes);
        assertTrue(nodes <= 512, "List options expanded to " + nodes + " nodes");
        assertTrue(copiedNodes <= 512, "Player command copy expanded to " + copiedNodes + " nodes");
    }

    @Test
    void localAndRemoteBranchesStaySmall(@TempDir Path directory) {
        var root = new CommandHarness(directory).dispatcher.getRoot().getChild("wp");
        int local = count(root.getChild("list"));
        int remote = count(root.getChild("remote"));
        System.out.printf("Command branch nodes: local=%d, remote=%d%n", local, remote);
        assertTrue(local <= 2048, "Local list expanded to " + local + " nodes");
        assertTrue(remote <= 2048, "Remote commands expanded to " + remote + " nodes");
    }

    @Test
    void optionCombinationsKeepTheirValuesAndNativeArgumentBounds() throws Exception {
        var dispatcher = dispatcher();
        // These are the documented option order and the page/search suffixes used by chat controls.
        for (String pattern : List.of("", "search \"head query\"", "sort %s", "sort %s order %s",
                "sort %s order %s page 2 limit 3 view %s", "sort %s page 2 limit 3 view %s",
                "page 2 limit 3 view %s", "limit 3 view %s page 2", "view %s page 2",
                "sort %s order %s limit 3 view %s page 2", "sort %s limit 3 page 2",
                "sort %s view %s page 2", "page 2", "limit 3", "view %s")) {
            for (SortMode mode : SortMode.values()) {
                for (boolean descending : List.of(false, true)) {
                    for (ListView view : List.of(ListView.LISTS, ListView.TREE, ListView.FLAT)) {
                        String suffix = pattern.replace("sort %s", "sort " + mode.name().toLowerCase(java.util.Locale.ROOT))
                                .replace("order %s", "order " + (descending ? "descending" : "ascending"))
                                .replace("view %s", "view " + view.id());
                        if (mode == SortMode.DEFAULT && pattern.contains("order")) continue;
                        for (String prefix : List.of("", "search \"head query\" ")) {
                            for (String tail : List.of("", " search \"tail query\"")) {
                                // A search can be followed by other options, but not immediately by another search.
                                if (suffix.isEmpty() && !prefix.isEmpty() && !tail.isEmpty()) continue;
                                if (suffix.startsWith("search") && (!prefix.isEmpty() || !tail.isEmpty())) continue;
                                String input = String.join(" ", java.util.stream.Stream.of("list", prefix.strip(), suffix, tail.strip())
                                        .filter(value -> !value.isEmpty()).toList());
                                assertEquals(1, dispatcher.execute(input, "source"), input);
                                assertEquals(new Selection(pattern.contains("sort") ? mode : SortMode.DEFAULT,
                                        pattern.contains("order") && descending,
                                        pattern.contains("view") ? view : ListView.DEFAULT,
                                        !tail.isEmpty() ? "tail query" : !prefix.isEmpty() || suffix.startsWith("search") ? "head query" : null,
                                        pattern.contains("page") ? 2 : 1, pattern.contains("limit") ? 3 : 5), selected, input);
                            }
                        }
                    }
                }
            }
        }
        for (String suffix : List.of("sort invalid", "sort NAME", "sort name order sideways", "view default",
                "view invalid", "sort default order descending", "page 0", "limit 0", "limit 101")) {
            selected = null;
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("list " + suffix, "source"), suffix);
            assertNull(selected, suffix);
        }
    }

    @Test
    void valueSuggestionsAndExecutionWorkThroughNestedRedirects() throws Exception {
        var dispatcher = dispatcher();
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<String>literal("run")
                .redirect(dispatcher.getRoot()));
        for (String prefix : List.of("", "run ", "run run ")) {
            assertEquals(List.of("color", "default", "distance", "name"), suggestions(dispatcher, prefix + "list sort "));
            assertEquals(List.of("distance"), suggestions(dispatcher, prefix + "list sort di"));
            assertEquals(List.of("ascending", "descending"), suggestions(dispatcher, prefix + "list sort name order "));
            assertEquals(List.of("descending"), suggestions(dispatcher, prefix + "list sort name order de"));
            assertTrue(suggestions(dispatcher, prefix + "list sort default order ").isEmpty());
            assertEquals(List.of("flat", "lists", "tree"), suggestions(dispatcher,
                    prefix + "list search village sort name order descending limit 3 view "));
            String input = prefix + "list sort color order descending limit 3 view tree page 2 search village";
            assertEquals(1, dispatcher.execute(input, "source"));
            assertEquals(new Selection(SortMode.COLOR, true, ListView.TREE, "village", 2, 3), selected);
        }
    }

    @Test
    void invalidValuesReportTheirPositionBeforeRunningTheCommand() {
        var dispatcher = dispatcher();
        for (String input : List.of("list sort invalid", "list sort name order invalid", "list view invalid")) {
            selected = null;
            var failure = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(input, "source"));
            assertEquals(input.indexOf("invalid"), failure.getCursor());
            assertNull(selected);
        }
    }

    private static List<String> suggestions(CommandDispatcher<String> dispatcher, String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, "source")).join().getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
    }

    private static String optionalString(CommandContext<String> context, String name) {
        try {
            return getString(context, name);
        } catch (IllegalArgumentException absent) {
            return null;
        }
    }

    private static int optionalInteger(CommandContext<String> context, String name, int fallback) {
        try {
            return getInteger(context, name);
        } catch (IllegalArgumentException absent) {
            return fallback;
        }
    }

    private static int count(CommandNode<?> root) {
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        visit(root, seen);
        return seen.size();
    }

    private static void visit(CommandNode<?> node, Set<CommandNode<?>> seen) {
        if (seen.add(node)) node.getChildren().forEach(child -> visit(child, seen));
    }

    private static <S> CommandNode<S> copy(CommandNode<S> source) {
        CommandNode<S> target = source instanceof com.mojang.brigadier.tree.RootCommandNode
                ? new com.mojang.brigadier.tree.RootCommandNode<>() : source.createBuilder().build();
        for (var child : source.getChildren()) target.addChild(copy(child));
        return target;
    }
}
