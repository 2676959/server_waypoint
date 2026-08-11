package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreWaypointCommandListTest {
    @TempDir
    private Path tempDir;
    private CommandDispatcher<TestSource> dispatcher;
    private TestMessageSender sender;
    private TestSource source;
    private WaypointServerCore server;
    private Config originalConfig;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        this.server = new WaypointServerCore(this.tempDir) {
        };
        this.server.putWaypointList(
                "overworld",
                new WaypointList("bases", 1, waypoints("base", 12))
        );
        this.server.putWaypointList("overworld", new WaypointList(
                "search",
                1,
                waypoints("reserved list waypoint", 12)
        ));
        this.server.putWaypointList("overworld", new WaypointList(
                "",
                1,
                waypoints("empty-name list waypoint", 12)
        ));

        this.sender = new TestMessageSender();
        this.source = new TestSource("overworld", new WaypointPos(0, 64, 0));
        this.dispatcher = new CommandDispatcher<>();
        new TestWaypointCommand(this.server, this.sender).register(this.dispatcher);
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void keepsOldListScopesAndAcceptsCombinedQueryOptions() {
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld bases", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld search", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld \"search\"", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all search base sort name order descending page 1 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list search base sort distance page 1 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list overworld sort color order descending limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list overworld bases search base sort name page 2 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort name order descending search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort distance order ascending page 1 limit 5 search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all view flat", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort name order descending view flat search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all sort default", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all limit 20", this.source));
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp list all limit 101", this.source)
        );
    }

    @Test
    void helpLinksDetailedTopicsAndSuggestsCommandPrefixes() throws CommandSyntaxException {
        this.dispatcher.execute("wp help", this.source);

        Component help = lastMessage();
        String helpText = plainText(help);
        assertTrue(helpText.contains("/wp list"));
        assertTrue(helpText.contains("/wp download [<dimension> [<list-identifier> [<waypoint-identifier>]]]"));
        assertTrue(helpText.contains("/wp add"));
        assertTrue(helpText.contains("/wp edit"));
        assertTrue(helpText.contains("/wp navigate"));
        assertTrue(helpText.contains("/wp remove <dimension> <list-identifier> [<waypoint-identifier>]"));
        assertTrue(helpText.contains("/wp tp <dimension> <list-identifier> <waypoint-identifier>"));
        assertTrue(helpText.contains("/wp reload"));
        assertTrue(translationKeys(help).containsAll(List.of(
                "waypoint.help.title",
                "waypoint.help.list",
                "waypoint.help.download",
                "waypoint.help.navigate",
                "waypoint.help.add",
                "waypoint.help.edit",
                "waypoint.help.remove",
                "waypoint.help.tp",
                "waypoint.help.reload",
                "waypoint.help.click_to_suggest",
                "waypoint.help.click_for_details"
        )));
        assertEquals(List.of(
                "/wp list ",
                "/wp download ",
                "/wp navigate ",
                "/wp add ",
                "/wp edit ",
                "/wp remove ",
                "/wp tp ",
                "/wp reload"
        ), suggestedCommands(help));
        assertEquals(List.of(
                "/wp help list",
                "/wp help navigate",
                "/wp help add",
                "/wp help edit"
        ), runCommands(help));
    }

    @Test
    void addHelpShowsAllFormsArgumentsAndExamples() throws CommandSyntaxException {
        this.dispatcher.execute("wp help add", this.source);

        Component help = lastMessage();
        String helpText = plainText(help);
        assertTrue(helpText.contains("/wp add <dimension> <list-identifier>"));
        assertTrue(helpText.contains("/wp add <position> <list-identifier> <waypoint-identifier>"));
        assertTrue(helpText.contains(
                "/wp add <position> <list-identifier> <waypoint-identifier> <initials> <color> <yaw> <global>"
        ));
        assertTrue(helpText.contains(
                "/wp add <dimension> <list-identifier> <position> <waypoint-identifier> <initials> <color> <yaw> <global>"
        ));
        assertTrue(suggestedCommands(help).contains(
                "/wp add minecraft:overworld \"Home Bases\" ~ ~ ~ \"Main Home\" MH gold 0 true"
        ));
        assertTrue(translationKeys(help).containsAll(List.of(
                "waypoint.help.add.title",
                "waypoint.help.add.summary",
                "waypoint.help.section.usage",
                "waypoint.help.section.arguments",
                "waypoint.help.section.examples"
        )));
        assertEquals(TextColor.color(0x55FF55), textColor(help, "<dimension>"));
        assertEquals(TextColor.color(0xFFAA00), textColor(help, "<list-identifier>"));
        assertEquals(TextColor.color(0x55AAFF), textColor(help, "<position>"));
        assertEquals(TextColor.color(0xFFFF55), textColor(help, "<waypoint-identifier>"));
        assertEquals(TextColor.color(0xAAAAFF), textColor(help, "<initials>"));
        assertEquals(TextColor.color(0xFF5555), textColor(help, "<color>"));
        assertEquals(TextColor.color(0x00D5A0), textColor(help, "<yaw>"));
        assertEquals(TextColor.color(0xC77DFF), textColor(help, "<global>"));
        assertEquals(TextColor.color(0x55FF55), textColor(help, "minecraft:overworld"));
        assertEquals(TextColor.color(0xFFAA00), textColor(help, "\"Home Bases\""));
        assertEquals(TextColor.color(0x55AAFF), textColor(help, "~ ~ ~"));
        assertEquals(TextColor.color(0xFFFF55), textColor(help, "\"Main Home\""));
        assertEquals(TextColor.color(0xAAAAFF), textColor(help, "MH"));
        assertEquals(TextColor.color(0xFF5555), textColor(help, "gold"));
        assertEquals(TextColor.color(0x00D5A0), textColor(help, "0"));
        assertEquals(TextColor.color(0xC77DFF), textColor(help, "true"));
        assertEquals(List.of("/wp help"), runCommands(help));
    }

    @Test
    void editHelpShowsPatchRoutesAndExample() throws CommandSyntaxException {
        this.dispatcher.execute("wp help edit", this.source);

        Component help = lastMessage();
        String helpText = plainText(help);
        assertTrue(helpText.contains("/wp edit list <dimension> <list-identifier> set identifier <identifier>"));
        assertTrue(helpText.contains("/wp edit waypoint <dimension> <list-identifier> <waypoint-identifier> set <property> <value>"));
        assertTrue(helpText.contains("clear <display-name|keywords|description>"));
        assertTrue(suggestedCommands(help).contains(
                "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" "
                        + "set identifier \"Mountain Home\""
        ));
        assertTrue(translationKeys(help).containsAll(List.of(
                "waypoint.help.edit.title",
                "waypoint.help.edit.summary",
                "waypoint.help.edit.usage",
                "waypoint.help.edit.example.full"
        )));
        assertEquals(TextColor.color(0xFF55FF), textColor(help, "\"Mountain Home\""));
        assertEquals(List.of("/wp help"), runCommands(help));
    }

    @Test
    void addUsesExactIdentifierAndPatchEditSetsDisplayName() throws CommandSyntaxException {
        String name = "{\"text\":\"Golden Beacon\",\"color\":\"gold\"}";
        String description = "{\"text\":\"Near spawn\",\"italic\":true}";
        String commandPrefix = "wp add overworld bases position "
                + StringArgumentType.escapeIfRequired(name)
                + " GB FFAA00 45 true";

        List<String> initialsSuggestions = this.dispatcher.getCompletionSuggestions(
                        this.dispatcher.parse(
                                "wp add overworld bases position "
                                        + StringArgumentType.escapeIfRequired(name)
                                        + " ",
                                this.source
                        )
                ).join().getList().stream()
                .map(suggestion -> suggestion.getText())
                .toList();
        assertTrue(initialsSuggestions.contains("GB"));

        this.dispatcher.execute(
                commandPrefix
                        + " " + StringArgumentType.escapeIfRequired("home, mining")
                        + " " + StringArgumentType.escapeIfRequired(description),
                this.source
        );

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertNotNull(bases);
        SimpleWaypoint waypoint = bases.getWaypointByName(name);
        assertNotNull(waypoint);
        assertEquals(name, waypoint.name());
        assertEquals(name, waypoint.displayName());
        assertFalse(waypoint.hasDisplayNameOverride());
        assertEquals(List.of("home", "mining"), waypoint.keywords());
        assertEquals(description, waypoint.description());

        this.dispatcher.execute(
                "wp edit waypoint overworld bases " + StringArgumentType.escapeIfRequired(name)
                        + " set display-name \"Golden Beacon\"",
                this.source
        );
        assertEquals("Golden Beacon", bases.getWaypointByName(name).displayName());
        assertTrue(bases.getWaypointByName(name).hasDisplayNameOverride());

        this.dispatcher.execute(
                "wp add overworld bases position \"Legacy Marker\" LM FFAA00 45 true",
                this.source
        );
        SimpleWaypoint plainWaypoint = bases.getWaypointByName("Legacy Marker");
        assertNotNull(plainWaypoint);
        assertEquals("Legacy Marker", plainWaypoint.displayName());
        assertEquals(List.of(), plainWaypoint.keywords());
        assertEquals("", plainWaypoint.description());
    }

    @Test
    void addRejectsCaseInsensitiveDuplicateKeywords() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp add overworld bases position duplicate D FFAA00 0 true \"home, HOME\"",
                this.source
        );

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertNull(bases.getWaypointByName("duplicate"));
        assertEquals(1, this.sender.errors.size());
        assertTrue(this.sender.errors.get(0).toString().contains("argument.keywords.duplicate"));
    }

    @Test
    void renameFeedbackBuildsDetailsControlsFromTheAfterSnapshot() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld bases position old O FFAA00 0 true", this.source);
        this.sender.messages.clear();

        this.dispatcher.execute(
                "wp edit waypoint overworld bases old set identifier \"new identifier\"",
                this.source
        );

        Component details = lastMessage();
        assertTrue(plainText(details).contains("new identifier"));
        List<String> commands = new ArrayList<>();
        commands.addAll(runCommands(details));
        commands.addAll(suggestedCommands(details));
        assertTrue(commands.stream().anyMatch(command -> command.contains("\"new identifier\"")));
        assertFalse(commands.stream().anyMatch(command -> command.contains(" old ")));
    }

    @Test
    void addListStoresTheExactIdentifierWithoutDisplayNameOverride() throws CommandSyntaxException {
        String identifier = "{\"text\":\"Travel Hubs\",\"color\":\"aqua\"}";

        this.dispatcher.execute(
                "wp add overworld " + StringArgumentType.escapeIfRequired(identifier),
                this.source
        );

        WaypointList waypointList = this.server.getWaypointFileManager("overworld")
                .getWaypointListByName(identifier);
        assertNotNull(waypointList);
        assertEquals(identifier, waypointList.name());
        assertEquals(identifier, waypointList.displayName());
        assertFalse(waypointList.hasDisplayNameOverride());
    }

    @Test
    void emptyIdentifiersRoundTripThroughAddAndDetailsCommands() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld \"\"", this.source);
        this.dispatcher.execute("wp add overworld \"\" position \"\" E FFAA00 0 true", this.source);

        WaypointList list = this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("");
        assertNotNull(list);
        assertNotNull(list.getWaypointByName(""));
        assertFalse(list.hasDisplayNameOverride());
        assertFalse(list.getWaypointByName("").hasDisplayNameOverride());
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp details waypoint overworld \"\" \"\"",
                this.source
        ));
    }

    @Test
    void addAcceptsLongIdentifiersButRejectsDescriptionsOverTheirLimits() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld " + "l".repeat(257), this.source);

        assertEquals(0, this.sender.errors.size());
        assertNotNull(this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("l".repeat(257)));

        this.sender.errors.clear();
        this.dispatcher.execute(
                "wp add overworld bases position marker M FFAA00 0 true \"\" " + "d".repeat(2049),
                this.source
        );

        assertEquals(1, this.sender.errors.size());
        assertTrue(this.sender.errors.get(0).toString().contains("argument.text.too_long"));
        assertNull(this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("bases")
                .getWaypointByName("marker"));
    }

    @Test
    void listHelpShowsScopesOrderedOptionsAndExamples() throws CommandSyntaxException {
        this.dispatcher.execute("wp help list", this.source);

        Component help = lastMessage();
        String helpText = plainText(help);
        assertTrue(helpText.contains("/wp list all"));
        assertTrue(helpText.contains("/wp list <dimension> <list>"));
        assertTrue(helpText.contains(
                "[search <query>] [sort <mode> [order <direction>]] [page <number>] [limit <number>] [view <view>]"
        ));
        assertTrue(helpText.contains("search → sort → order → page → limit → view"));
        assertTrue(helpText.contains("sort → order → page → limit → view → search"));
        assertTrue(suggestedCommands(help).contains(
                "/wp list all search home sort distance order ascending page 1 limit 10 view flat"
        ));
        assertTrue(suggestedCommands(help).contains(
                "/wp list minecraft:overworld \"Home Bases\" sort name order descending limit 20"
        ));
        assertTrue(translationKeys(help).containsAll(List.of(
                "waypoint.help.list.title",
                "waypoint.help.list.summary",
                "waypoint.help.list.usage.options",
                "waypoint.help.list.argument.view",
                "waypoint.help.list.argument.order"
        )));
        assertEquals(TextColor.color(0xFF79C6), textColor(help, "<query>"));
        assertEquals(TextColor.color(0xF1FA8C), textColor(help, "<mode>"));
        assertEquals(TextColor.color(0x8BE9FD), textColor(help, "<direction>"));
        assertEquals(TextColor.color(0x50FA7B), textColor(help, "<number>"));
        assertEquals(TextColor.color(0xFF79C6), textColor(help, "home"));
        assertEquals(TextColor.color(0xF1FA8C), textColor(help, "distance"));
        assertEquals(TextColor.color(0x8BE9FD), textColor(help, "ascending"));
        assertEquals(TextColor.color(0x50FA7B), textColor(help, "10"));
        assertEquals(List.of("/wp help"), runCommands(help));
    }

    @Test
    void helpOmitsCommandsTheSourceCannotUse() throws CommandSyntaxException {
        TestMessageSender restrictedSender = new TestMessageSender();
        CommandDispatcher<TestSource> restrictedDispatcher = new CommandDispatcher<>();
        new TestWaypointCommand(
                this.server,
                restrictedSender,
                TestWaypointCommand.permissionManager(false)
        ).register(restrictedDispatcher);

        restrictedDispatcher.execute("wp help", this.source);

        Component help = restrictedSender.messages.get(0);
        assertEquals(List.of("/wp list ", "/wp download "), suggestedCommands(help));
        assertEquals(List.of("/wp help list"), runCommands(help));
        assertDoesNotThrow(() -> restrictedDispatcher.execute("wp help list", this.source));
        assertThrows(
                CommandSyntaxException.class,
                () -> restrictedDispatcher.execute("wp help add", this.source)
        );
        assertThrows(
                CommandSyntaxException.class,
                () -> restrictedDispatcher.execute("wp help edit", this.source)
        );
    }

    @Test
    void searchUsesFilteredRowsAndNextPagePreservesAllOptions() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp list all search \"base 12\" sort name limit 5",
                this.source
        );

        String filteredText = plainText(lastMessage());
        assertTrue(filteredText.contains("base 12"));
        assertFalse(filteredText.contains("base 11"));

        this.sender.messages.clear();
        this.dispatcher.execute(
                "wp list all search base sort name order descending page 1 limit 5",
                this.source
        );

        List<String> runCommands = runCommands(lastMessage());
        assertTrue(runCommands.contains(
                "/wp list all search base sort name order descending page 2 limit 5"
        ));
    }

    @Test
    void listFeedbackSuggestsSearchForTheCurrentTarget() throws CommandSyntaxException {
        this.dispatcher.execute("wp list", this.source);

        Component currentDimensionList = lastMessage();
        assertEquals("/wp list overworld search ", listSearchSuggestion(currentDimensionList));
        assertTrue(translationKeys(currentDimensionList).contains("button.list.search"));

        this.sender.messages.clear();
        this.dispatcher.execute("wp list all", this.source);

        assertEquals("/wp list all search ", listSearchSuggestion(lastMessage()));

        this.sender.messages.clear();
        this.dispatcher.execute("wp list overworld \"search\"", this.source);

        String searchSuggestion = listSearchSuggestion(lastMessage());
        assertEquals("/wp list overworld \"search\" search ", searchSuggestion);
        assertDoesNotThrow(() -> this.dispatcher.execute(searchSuggestion.substring(1) + "base", this.source));

        this.sender.messages.clear();
        this.dispatcher.execute(
                "wp list all sort name order descending page 2 limit 5",
                this.source
        );

        String sortedSearchSuggestion = listSearchSuggestion(lastMessage());
        assertEquals(
                "/wp list all sort name order descending search ",
                sortedSearchSuggestion
        );
        assertDoesNotThrow(() -> this.dispatcher.execute(
                sortedSearchSuggestion.substring(1) + "base",
                this.source
        ));
    }

    @Test
    void viewTogglePreservesListOptionsAndSwitchesTheRenderedShape() throws CommandSyntaxException {
        this.dispatcher.execute("wp list all view flat", this.source);

        assertTrue(plainText(lastMessage()).contains("overworld / bases /"));

        this.sender.messages.clear();
        this.dispatcher.execute(
                "wp list all search base sort name order descending page 2 limit 5 view flat",
                this.source
        );

        Component flatList = lastMessage();
        assertTrue(plainText(flatList).contains("overworld / bases /"));
        assertTrue(translationKeys(flatList).containsAll(List.of(
                "waypoint.list.view.tree",
                "button.list.view.tree"
        )));
        assertEquals(
                "/wp list all sort name order descending view flat search ",
                listSearchSuggestion(flatList)
        );
        assertTrue(runCommands(flatList).contains(
                "/wp list all search base sort name order descending page 1 limit 5 view flat"
        ));

        String treeViewCommand = runCommands(flatList).stream()
                .filter(command -> command.endsWith("view tree"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "/wp list all search base sort name order descending page 2 limit 5 view tree",
                treeViewCommand
        );

        this.sender.messages.clear();
        this.dispatcher.execute(treeViewCommand.substring(1), this.source);

        Component treeList = lastMessage();
        assertFalse(plainText(treeList).contains("overworld / bases /"));
        assertTrue(translationKeys(treeList).containsAll(List.of(
                "waypoint.list.view.flat",
                "button.list.view.flat"
        )));
        assertTrue(runCommands(treeList).contains(
                "/wp list all search base sort name order descending page 2 limit 5 view flat"
        ));
    }

    @Test
    void treePagesShowAllDimensionsAndTitlesSelectTheirScope() throws CommandSyntaxException {
        for (int index = 0; index < 4; index++) {
            String dimensionName = "dim" + index;
            String listName = index == 1 ? "list one" : "list" + index;
            this.server.putWaypointList(dimensionName, new WaypointList(
                    listName,
                    1,
                    List.of(waypoint("marker " + dimensionName, index))
            ));
        }

        this.sender.messages.clear();
        this.dispatcher.execute(
                "wp list all search marker sort name order descending page 2 limit 1 view tree",
                this.source
        );

        Component page = lastMessage();
        String pageText = plainText(page);
        assertTrue(pageText.contains("dim0\n  ...\ndim1\n"));
        assertTrue(pageText.contains("list one"));
        assertTrue(pageText.contains("dim2\n  ...\ndim3\n  ...\n"));
        assertEquals(3, countOccurrences(pageText, "  ...\n"));
        assertTrue(translationKeys(page).contains("button.list.dimension"));
        assertTrue(translationKeys(page).contains("button.list.waypoint_list"));
        assertEquals(TextColor.color(0xFFFF55), hoverTextColor(page, "dim0"));

        List<String> commands = runCommands(page);
        for (int index = 0; index < 4; index++) {
            assertTrue(commands.contains(
                    "/wp list dim" + index
                            + " search marker sort name order descending page 1 limit 1 view tree"
            ));
        }
        assertTrue(commands.contains(
                "/wp list dim1 \"list one\" search marker sort name order descending page 1 limit 1 view tree"
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list dim1 \"list one\" search marker sort name order descending page 1 limit 1 view tree",
                this.source
        ));
    }

    @Test
    void reservedListNameIsQuotedInSuggestionsAndPageLinks() throws CommandSyntaxException {
        List<String> suggestions = this.dispatcher.getCompletionSuggestions(
                        this.dispatcher.parse("wp list overworld ", this.source)
                ).join().getList().stream()
                .map(suggestion -> suggestion.getText())
                .toList();
        assertTrue(suggestions.contains("\"search\""));
        assertTrue(suggestions.contains("\"\""));

        this.dispatcher.execute("wp list overworld \"search\" limit 5", this.source);
        String nextPageCommand = runCommands(lastMessage()).stream()
                .filter(command -> command.contains(" page 2 "))
                .findFirst()
                .orElseThrow();

        assertEquals("/wp list overworld \"search\" page 2 limit 5", nextPageCommand);
        assertDoesNotThrow(() -> this.dispatcher.execute(nextPageCommand.substring(1), this.source));

        this.sender.messages.clear();
        this.dispatcher.execute("wp list overworld \"\" limit 5", this.source);
        String emptyNameNextPageCommand = runCommands(lastMessage()).stream()
                .filter(command -> command.contains(" page 2 "))
                .findFirst()
                .orElseThrow();
        assertEquals("/wp list overworld \"\" page 2 limit 5", emptyNameNextPageCommand);
        assertDoesNotThrow(() -> this.dispatcher.execute(emptyNameNextPageCommand.substring(1), this.source));
    }

    @Test
    void pagePastTheResultReportsTheLastAvailablePage() throws CommandSyntaxException {
        this.dispatcher.execute("wp list all page 99 limit 5", this.source);

        assertEquals(1, this.sender.errors.size());
        assertTrue(this.sender.errors.get(0).toString().contains("waypoint.list.page.invalid"));
    }

    @Test
    void configuredDefaultPageLimitIsUsedUnlessTheCommandOverridesIt() throws CommandSyntaxException {
        this.server.loadConfig(new StringReader("""
                {
                  "defaultPageLimit": 4
                }
                """));

        this.dispatcher.execute("wp list overworld bases", this.source);

        assertTrue(runCommands(lastMessage()).contains(
                "/wp list overworld bases page 2 limit 4"
        ));

        this.sender.messages.clear();
        this.dispatcher.execute("wp list overworld bases limit 7", this.source);

        assertTrue(runCommands(lastMessage()).contains(
                "/wp list overworld bases page 2 limit 7"
        ));
    }

    @Test
    void sortControlsPreserveTheQueryAndResetThePage() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp list all search base sort name order descending page 2 limit 5",
                this.source
        );

        List<String> runCommands = runCommands(lastMessage());
        assertTrue(runCommands.contains(
                "/wp list all search base page 1 limit 5"
        ));
        assertTrue(runCommands.contains(
                "/wp list all search base sort distance page 1 limit 5"
        ));
        assertTrue(runCommands.contains(
                "/wp list all search base sort color page 1 limit 5"
        ));
        assertTrue(runCommands.contains(
                "/wp list all search base sort name page 1 limit 5"
        ));
    }

    @Test
    void sortControlsAreAvailableOnOnePageAndDefaultOrderIsDisabled() throws CommandSyntaxException {
        this.dispatcher.execute("wp list overworld bases limit 20", this.source);

        List<String> listCommands = runCommands(lastMessage()).stream()
                .filter(command -> command.startsWith("/wp list"))
                .toList();
        assertEquals(List.of(
                "/wp list overworld bases page 1 limit 20 view flat",
                "/wp list overworld bases sort name page 1 limit 20",
                "/wp list overworld bases sort distance page 1 limit 20",
                "/wp list overworld bases sort color page 1 limit 20"
        ), listCommands);
    }

    private Component lastMessage() {
        return this.sender.messages.get(this.sender.messages.size() - 1);
    }

    private static List<SimpleWaypoint> waypoints(String prefix, int count) {
        List<SimpleWaypoint> waypoints = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            waypoints.add(waypoint(prefix + " " + index, index));
        }
        return waypoints;
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(name, name.substring(0, 1), new WaypointPos(x, 64, 0), 0xFFFFFF, 0, false);
    }

    private static String plainText(Component component) {
        StringBuilder text = new StringBuilder();
        appendPlainText(component, text);
        return text.toString();
    }

    private static void appendPlainText(Component component, StringBuilder text) {
        if (component instanceof TextComponent textComponent) {
            text.append(textComponent.content());
        }
        for (Component child : component.children()) {
            appendPlainText(child, text);
        }
    }

    private static TextColor textColor(Component component, String content) {
        if (component instanceof TextComponent textComponent
                && textComponent.content().equals(content)) {
            return component.color();
        }
        if (component instanceof TranslatableComponent translatableComponent) {
            for (var argument : translatableComponent.arguments()) {
                if (argument.value() instanceof Component argumentComponent) {
                    TextColor color = textColor(argumentComponent, content);
                    if (color != null) {
                        return color;
                    }
                }
            }
        }
        for (Component child : component.children()) {
            TextColor color = textColor(child, content);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static TextColor hoverTextColor(Component component, String content) {
        if (component.hoverEvent() != null
                && component.hoverEvent().action()
                == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT) {
            Object hoverValue = component.hoverEvent().value();
            if (hoverValue instanceof Component hoverComponent) {
                TextColor color = textColor(hoverComponent, content);
                if (color != null) {
                    return color;
                }
            }
        }
        for (Component child : component.children()) {
            TextColor color = hoverTextColor(child, content);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static List<String> runCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectRunCommands(component, commands);
        return commands;
    }

    private static List<String> suggestedCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectSuggestedCommands(component, commands);
        return commands;
    }

    private static String listSearchSuggestion(Component component) {
        return suggestedCommands(component).stream()
                .filter(command -> command.startsWith("/wp list"))
                .findFirst()
                .orElseThrow();
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(substring, offset)) >= 0) {
            count++;
            offset += substring.length();
        }
        return count;
    }

    private static void collectSuggestedCommands(Component component, List<String> commands) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.SUGGEST_COMMAND) {
            commands.add(clickEvent.value());
        }
        for (Component child : component.children()) {
            collectSuggestedCommands(child, commands);
        }
    }

    private static List<String> translationKeys(Component component) {
        List<String> keys = new ArrayList<>();
        collectTranslationKeys(component, keys);
        return keys;
    }

    private static void collectTranslationKeys(Component component, List<String> keys) {
        if (component instanceof TranslatableComponent translatableComponent) {
            keys.add(translatableComponent.key());
        }
        if (component.hoverEvent() != null
                && component.hoverEvent().action() == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT) {
            Object hoverValue = component.hoverEvent().value();
            if (hoverValue instanceof Component hoverComponent) {
                collectTranslationKeys(hoverComponent, keys);
            }
        }
        for (Component child : component.children()) {
            collectTranslationKeys(child, keys);
        }
    }

    private static void collectRunCommands(Component component, List<String> commands) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.RUN_COMMAND) {
            commands.add(clickEvent.value());
        }
        for (Component child : component.children()) {
            collectRunCommands(child, commands);
        }
    }

    private record TestSource(String dimensionName, WaypointPos position) {
    }

    private static final class TestWaypointCommand
            extends CoreWaypointCommand<TestSource, String, Object, String, String> {
        private TestWaypointCommand(WaypointServerCore server, TestMessageSender sender) {
            this(server, sender, permissionManager(true));
        }

        private TestWaypointCommand(
                WaypointServerCore server,
                TestMessageSender sender,
                PermissionManager<TestSource, String, Object> permissionManager
        ) {
            super(
                    server,
                    sender,
                    permissionManager,
                    navigationService(),
                    StringArgumentType::string,
                    StringArgumentType::string
            );
        }

        private static NavigationService<Object> navigationService() {
            NavigationPlatform<Object> platform = new NavigationPlatform<>() {
                @Override
                public UUID playerUuid(Object player) {
                    return new UUID(0, 0);
                }

                @Override
                public void executePlayer(UUID playerUuid, java.util.function.Consumer<Object> action) {
                }

                @Override
                public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                    return NavigationSnapshot.wrongDimension();
                }
            };
            return new NavigationService<>(platform, List.of());
        }

        @Override
        protected String toDimensionName(String dimensionArgument) {
            return dimensionArgument;
        }

        @Override
        protected WaypointPos toWaypointPos(TestSource source, String blockPositionArgument) {
            return source.position();
        }

        @Override
        protected boolean isDimensionValid(TestSource source, String dimensionArgument) {
            return true;
        }

        @Override
        protected void executeByServer(TestSource source, Runnable task) {
            task.run();
        }

        @Override
        protected String getSourceDimension(TestSource source) {
            return source.dimensionName();
        }

        @Override
        protected WaypointPos getSourcePosition(TestSource source) {
            return source.position();
        }

        @Override
        protected float getSourceYaw(TestSource source) {
            return 0;
        }

        @Override
        protected Object getPlayer(TestSource source) {
            return null;
        }

        @Override
        protected String getPlayerName(Object player) {
            return "player";
        }

        @Override
        protected void teleportPlayer(
                TestSource source,
                Object player,
                String dimensionArgument,
                WaypointPos pos,
                int yaw
        ) {
        }

        @Override
        protected Message getMessageFromComponent(Component component) {
            return component::toString;
        }

        private static PermissionManager<TestSource, String, Object> permissionManager(
                boolean allowPrivilegedCommands
        ) {
            PermissionKeys<String> keys = new PermissionKeys<>() {
                @Override
                protected PermissionKey createAddPermissionKey() {
                    return new PermissionKey("add");
                }

                @Override
                protected PermissionKey createEditPermissionKey() {
                    return new PermissionKey("edit");
                }

                @Override
                protected PermissionKey createRemovePermissionKey() {
                    return new PermissionKey("remove");
                }

                @Override
                protected PermissionKey createNavigatePermissionKey() {
                    return new PermissionKey("navigate");
                }

                @Override
                protected PermissionKey createTpPermissionKey() {
                    return new PermissionKey("tp");
                }

                @Override
                protected PermissionKey createReloadPermissionKey() {
                    return new PermissionKey("reload");
                }
            };
            return new PermissionManager<>(keys) {
                @Override
                public boolean hasPermission(
                        TestSource source,
                        PermissionKeys<String>.PermissionKey key,
                        int defaultLevel
                ) {
                    return allowPrivilegedCommands;
                }

                @Override
                public boolean checkPlayerPermission(
                        Object player,
                        PermissionKeys<String>.PermissionKey key,
                        int defaultLevel
                ) {
                    return allowPrivilegedCommands;
                }
            };
        }
    }

    private static final class TestMessageSender implements PlatformMessageSender<TestSource, Object> {
        private final List<Component> messages = new ArrayList<>();
        private final List<Component> errors = new ArrayList<>();

        @Override
        public void sendMessage(TestSource source, Component component) {
            this.messages.add(component);
        }

        @Override
        public void sendPlayerMessage(Object player, Component component) {
        }

        @Override
        public void sendError(TestSource source, Component component) {
            this.errors.add(component);
        }

        @Override
        public void sendPacket(TestSource source, MessageBuffer packet) {
        }

        @Override
        public void sendPlayerPacket(Object player, MessageBuffer packet) {
        }

        @Override
        public Iterable<?> getBroadcastPlayers(TestSource source) {
            return List.of();
        }

        @Override
        public Component getSenderName(TestSource source) {
            return Component.text("tester");
        }
    }
}
