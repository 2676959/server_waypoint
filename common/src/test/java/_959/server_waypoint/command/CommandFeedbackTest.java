package _959.server_waypoint.command;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Viewer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /wp feedback through the command: the viewer, the receiver and every screen's wiring. */
class CommandFeedbackTest {
    @TempDir
    Path directory;
    private Config originalConfig;
    CommandHarness harness;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        this.harness = new CommandHarness(this.directory);
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void theViewerFollowsPermissionsTheModTheReceiverAndThePosition() {
        Viewer viewer = this.harness.command.viewer(CommandHarness.player().withPermissions("add", "tp"));

        assertEquals(Set.of(Viewer.Permission.ADD, Viewer.Permission.TP), viewer.permissions());
        assertTrue(viewer.hasMod());
        assertFalse(viewer.plainText());
        assertEquals("minecraft:overworld", viewer.dimension());
        assertEquals(new WaypointPos(100, 64, -20), viewer.position());
        assertEquals(37F, viewer.yaw());
        assertTrue(this.harness.command.viewer(CommandHarness.player()).can(Viewer.Permission.REMOTE_TP));

        Viewer console = this.harness.command.viewer(CommandHarness.console());
        assertTrue(console.plainText());
        assertFalse(console.hasMod());
        assertFalse(console.can(Viewer.Permission.REMOTE_TP));

        assertTrue(this.harness.command.viewer(CommandHarness.player().readByConsole()).plainText());
        this.harness.sender.handshake = false;
        assertFalse(this.harness.command.viewer(CommandHarness.player()).hasMod());
    }

    @Test
    void theMenuAnswersPlayersAndTheHelpIndexAnswersPlainText() {
        assertEquals("Server Waypoint   Open GUI · Help · Reload",
                lines(this.harness.run(CommandHarness.player(), "wp")).get(0));
        assertEquals("Server Waypoint help", lines(this.harness.run(CommandHarness.console(), "wp")).get(0));
        assertEquals("Server Waypoint help",
                lines(this.harness.run(CommandHarness.player().readByConsole(), "wp")).get(0));
    }

    @Test
    void helpTopicsFollowPermissions() {
        CommandHarness.Source member = CommandHarness.player().withPermissions("navigate");

        assertEquals("Commands  List · Navigate · Download", lines(this.harness.run(member, "wp help")).get(2));
        this.harness.fails(member, "wp help add");
        assertEquals("Teleport  hover a line for details, click to use it",
                lines(this.harness.run(CommandHarness.player(), "wp help tp")).get(0));
        assertEquals("Download  hover a line for details, click to use it",
                lines(this.harness.run(member, "wp help download")).get(0));
    }

    @Test
    void listCommandsShowTheNewScreensAndTheirLinksRunAgain() {
        this.harness.addList("minecraft:overworld", "Farms",
                CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();

        assertEquals("Overworld ⏷  1 list · 1 waypoint · All", lines(this.harness.run(player, "wp list")).get(0));
        for (String command : List.of("wp list minecraft:overworld view lists", "wp list minecraft:overworld view tree page 1",
                "wp list minecraft:overworld search iron sort name order descending limit 5 view flat page 1",
                "wp list minecraft:overworld Farms limit 5 page 1")) {
            Component screen = this.harness.run(player, command);
            for (String click : runCommands(screen)) {
                if (click.startsWith("/wp list")) {
                    this.harness.run(player, click.substring(1));
                }
            }
        }
        assertEquals("✘ No list called Farm in Overworld. Browse lists",
                lines(this.harness.run(player, "wp list minecraft:overworld Farm")).get(0));
        assertEquals("✘ No dimension called test:missing. Dimensions",
                lines(this.harness.run(player, "wp list test:missing")).get(0));
    }

    @Test
    void reservedAndEmptyListNamesAreQuotedInTheirPageLinks() {
        List<SimpleWaypoint> pillars = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            pillars.add(CommandHarness.waypoint("Pillar " + index, "P", 0xFFFFFF, index, 64, 0));
        }
        this.harness.addList("minecraft:overworld", "search", pillars.toArray(SimpleWaypoint[]::new));
        this.harness.addList("minecraft:overworld", "", pillars.toArray(SimpleWaypoint[]::new));
        CommandHarness.Source player = CommandHarness.player();

        assertTrue(runCommands(this.harness.run(player, "wp list minecraft:overworld \"search\" limit 5"))
                .contains("/wp list minecraft:overworld \"search\" limit 5 page 2"));
        assertTrue(runCommands(this.harness.run(player, "wp list minecraft:overworld \"\""))
                .contains("/wp list minecraft:overworld \"\" page 2"));
        this.harness.run(player, "wp list minecraft:overworld \"search\" limit 5 page 2");
    }

    @Test
    void theConfiguredPageLimitStaysOutOfTheCommands() {
        this.harness.server.loadConfig(new java.io.StringReader("{\"defaultPageLimit\": 4}"));
        List<SimpleWaypoint> pillars = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            pillars.add(CommandHarness.waypoint("Pillar " + index, "P", 0xFFFFFF, index, 64, 0));
        }
        this.harness.addList("minecraft:overworld", "bases", pillars.toArray(SimpleWaypoint[]::new));

        assertTrue(runCommands(this.harness.run(CommandHarness.player(), "wp list minecraft:overworld bases"))
                .contains("/wp list minecraft:overworld bases page 2"));
        assertTrue(runCommands(this.harness.run(CommandHarness.player(), "wp list minecraft:overworld bases limit 5"))
                .contains("/wp list minecraft:overworld bases limit 5 page 2"));
    }

    @Test
    void commandsWithoutATargetShowThisDimensionAndWhatToClick() {
        this.harness.addList("minecraft:overworld", "Farms",
                CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));

        List<String> tp = lines(this.harness.run(CommandHarness.player(), "wp tp"));
        assertEquals("Click a waypoint's initials to teleport.", tp.get(0));
        assertEquals("Overworld ⏷  1 list · 1 waypoint · All", tp.get(1));
        assertEquals("Click a waypoint's name, then Remove.", lines(this.harness.run(CommandHarness.player(), "wp remove")).get(0));
        assertEquals("Click a waypoint's name to edit it.", lines(this.harness.run(CommandHarness.player(), "wp edit")).get(0));
        assertEquals("Click a waypoint's name for details.", lines(this.harness.run(CommandHarness.player(), "wp details")).get(0));
        assertEquals("Remove", lines(this.harness.run(CommandHarness.console(), "wp remove")).get(0));
    }

    @Test
    void quickAddWorksInAnyDimension() {
        this.harness.addList("minecraft:the_nether", "Hub");

        this.harness.run(CommandHarness.player(), "wp add minecraft:the_nether Hub 1 64 2 Portal");

        assertEquals(new WaypointPos(1, 64, 2), this.harness.server.getWaypointFileManager("minecraft:the_nether")
                .getWaypointListByName("Hub").getWaypointByName("Portal").pos());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
        assertEquals("Farms\n", render(PlatformMessageSender.forPlayer(text("Farms"))));
    }
}
