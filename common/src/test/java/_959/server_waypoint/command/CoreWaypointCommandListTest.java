package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        WaypointFileManager fileManager = this.server.getOrCreateWaypointFileManager("overworld");
        fileManager.addWaypointList(new WaypointList("bases", 1, waypoints("base", 12)));
        fileManager.addWaypointList(new WaypointList(
                "search",
                1,
                waypoints("reserved list waypoint", 12)
        ));
        fileManager.addWaypointList(new WaypointList(
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
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all sort default", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all limit 20", this.source));
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp list all limit 101", this.source)
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

    private static List<String> runCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectRunCommands(component, commands);
        return commands;
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
            super(
                    server,
                    sender,
                    permissionManager(),
                    StringArgumentType::string,
                    StringArgumentType::string
            );
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

        private static PermissionManager<TestSource, String, Object> permissionManager() {
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
                    return true;
                }

                @Override
                public boolean checkPlayerPermission(
                        Object player,
                        PermissionKeys<String>.PermissionKey key,
                        int defaultLevel
                ) {
                    return true;
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
