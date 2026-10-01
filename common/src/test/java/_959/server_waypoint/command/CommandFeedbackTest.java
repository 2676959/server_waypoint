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
    void theDimensionListAndAllDimensionsAnswerTheirCommands() {
        CommandHarness.Source player = CommandHarness.player();

        assertEquals(List.of("Dimensions  3 on this server", "Overworld · 0 ●", "No waypoints yet: Nether · End",
                "All dimensions · 0"), lines(this.harness.run(player, "wp list dimensions")));
        assertEquals("✘ Page 2 does not exist; the last page is 1. Last page",
                lines(this.harness.run(player, "wp list dimensions page 2")).get(0));
        assertEquals(List.of("All dimensions ⏷", "No lists yet."), lines(this.harness.run(player, "wp list all")));
        this.harness.addList("minecraft:the_nether", "Hub", CommandHarness.waypoint("Portal", "P", 0xFF5555, 1, 64, 1));
        assertEquals("Nether · 1", lines(this.harness.run(player, "wp list all")).get(1));
        assertEquals("Search \"portal\" · 1 match · Clear", lines(this.harness.run(player, "wp list all search portal")).get(1));
    }

    @Test
    void colourAndFacingWithoutAValueOpenTheirPickersAndRandomPicksAColour() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();
        String waypoint = "wp edit waypoint minecraft:overworld Farms \"Iron Farm\" ";

        assertEquals("Color · [IF] Iron Farm   now ■ #AAAAAA", lines(this.harness.run(player, waypoint + "set color")).get(0));
        assertEquals("Facing · [IF] Iron Farm   now 0° (south)", lines(this.harness.run(player, waypoint + "set yaw")).get(0));
        this.harness.run(player, waypoint + "set color random");
        this.harness.run(player, waypoint + "set yaw -90");
        assertEquals(-90, this.harness.server.getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("Farms").getWaypointByName("Iron Farm").yaw());
    }

    @Test
    void addWithoutArgumentsOpensTheAddPickerForPlayersOnly() {
        this.harness.addList("minecraft:overworld", "Farms");

        assertEquals(List.of("Add a waypoint at 100, 64, -20", "Into  Farms", "New list · Back"),
                lines(this.harness.run(CommandHarness.player(), "wp add")));
        assertEquals("✘ Only players can do that.", lines(this.harness.run(CommandHarness.console(), "wp add")).get(0));
        this.harness.run(CommandHarness.player(), "wp add page 1");
    }

    @Test
    void detailsAndEditsShowTheDetailsPanelWithTheResultOnTop() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();
        String edit = "wp edit waypoint minecraft:overworld Farms \"Iron Farm\" ";

        assertEquals("Overworld › Farms › [IF] Iron Farm",
                lines(this.harness.run(player, "wp details waypoint minecraft:overworld Farms \"Iron Farm\"")).get(0));
        List<String> edited = lines(this.harness.run(player, edit + "set yaw 90"));
        assertEquals("✔ Updated the facing", edited.get(0));
        assertEquals("[✎] Yaw: 90° (west)", edited.get(8));
        assertEquals("✔ Updated the visibility", lines(this.harness.run(player, edit + "set visibility local")).get(0));
        assertEquals("✔ Updated the position", lines(this.harness.run(player, edit + "set position ~ ~ ~")).get(0));
        assertEquals("Overworld › Farms  1 waypoint",
                lines(this.harness.run(player, "wp details list minecraft:overworld Farms")).get(0));
        this.harness.run(player, "wp edit list minecraft:overworld Farms set display-name \"Farm Row\"");
        assertEquals("✔ Cleared the display name",
                lines(this.harness.run(player, "wp edit list minecraft:overworld Farms clear display-name")).get(0));
    }

    @Test
    void theConsoleReadsDetailsWithoutButtons() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));

        List<String> details = lines(this.harness.run(CommandHarness.console(),
                "wp details waypoint minecraft:overworld Farms \"Iron Farm\""));

        assertEquals("Overworld (minecraft:overworld) › Farms › [IF] Iron Farm", details.get(0));
        assertEquals("Position: 300, 80, 150", details.get(5));
        assertEquals(11, details.size());
    }

    @Test
    void addingAnswersWithItsActionsAndTellsOnlyTheOtherPlayers() {
        this.harness.addList("minecraft:overworld", "Farms");
        CommandHarness.Source alex = CommandHarness.player();
        CommandHarness.Source sam = new CommandHarness.Source("Sam", "minecraft:the_nether", new WaypointPos(0, 64, 0), 0F,
                true, false, Set.of());
        this.harness.sender.online.addAll(List.of(alex, sam));

        Component result = this.harness.run(alex, "wp add minecraft:overworld Farms 80 66 40 \"Pumpkin Patch\" PP FFAA00 0 true");

        assertEquals("✔ Added [PP] Pumpkin Patch to Farms   Details · Navigate · Undo", render(result));
        assertEquals(1, this.harness.sender.toPlayers.size());
        assertEquals(sam, this.harness.sender.toPlayers.get(0).getKey());
        Component broadcast = this.harness.sender.toPlayers.get(0).getValue();
        assertEquals("Alex added [PP] Pumpkin Patch to Farms", render(broadcast));
        assertTrue(runCommands(broadcast).stream().noneMatch(command -> command.startsWith("/wp tp")));
    }

    @Test
    void removingOffersRestoreWhichPutsTheWaypointBack() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();

        Component removed = this.harness.run(player, "wp remove minecraft:overworld Farms \"Iron Farm\"");
        String restore = runCommands(removed).stream().filter(command -> command.startsWith("/wp restore ")).findFirst().orElseThrow();

        assertEquals("✔ Removed [IF] Iron Farm from Farms   Restore", render(removed));
        assertEquals("✔ Restored [IF] Iron Farm to Farms", render(this.harness.run(player, restore.substring(1))));
        assertTrue(render(this.harness.run(CommandHarness.console(), "wp remove minecraft:overworld Farms \"Iron Farm\""))
                .startsWith("✔ Removed [IF] Iron Farm from Farms. Restore with /wp restore "));
    }

    @Test
    void errorsAreOneRedLineWithARecoveryLink() {
        this.harness.addList("minecraft:overworld", "Farms");
        CommandHarness.Source player = CommandHarness.player();

        assertEquals("✘ No list called Farm in Overworld. Browse lists",
                render(this.harness.run(player, "wp details list minecraft:overworld Farm")));
        assertEquals("✘ No waypoint called Gate in Farms. Open Farms",
                render(this.harness.run(player, "wp tp minecraft:overworld Farms Gate")));
        assertEquals("✘ Overworld already has a list called Farms. Open",
                render(this.harness.run(player, "wp add minecraft:overworld Farms")));
        assertEquals("✘ Nothing changed.",
                render(this.harness.run(player, "wp edit list minecraft:overworld Farms set identifier Farms")));
        assertEquals(4, this.harness.sender.errors.size());
    }

    @Test
    void navigateWithoutArgumentsShowsWhereToStart() {
        CommandHarness.Source player = CommandHarness.player();

        assertEquals(List.of("Not navigating", "Open a waypoint and choose Navigate:  This dimension · All"),
                lines(this.harness.run(player, "wp navigate")));
        assertEquals("✘ You aren't navigating. Browse waypoints", render(this.harness.run(player, "wp navigate disable")));
        assertEquals("✘ Only players can do that.", render(this.harness.run(CommandHarness.console(), "wp navigate")));
    }

    @Test
    void uploadWithoutArgumentsOpensTheUploadPanelForPlayersWithTheMod() {
        assertEquals("Upload from your map mod", lines(this.harness.run(CommandHarness.player(), "wp upload")).get(0));
        assertEquals("Xaero's Minimap  Merge · Prefer mine",
                lines(this.harness.run(CommandHarness.player().withPermissions("upload"), "wp upload")).get(1));
        assertEquals("✘ Only players can do that.", render(this.harness.run(CommandHarness.console(), "wp upload")));
        this.harness.sender.handshake = false;
        assertEquals("✘ Uploading needs Server Waypoint on your client.",
                render(this.harness.run(CommandHarness.player(), "wp upload")));
    }

    @Test
    void requirementChecksNeverReadTheSourcesLevel() {
        CommandHarness.Source noLevel = new CommandHarness.Source("Help map", null, null, 0F, false, true,
                CommandHarness.EVERY_PERMISSION);
        var help = this.harness.dispatcher.getRoot().getChild("wp").getChild("help");

        assertEquals(9, this.harness.dispatcher.getSmartUsage(help, noLevel).size());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
        assertEquals("Farms\n", render(PlatformMessageSender.forPlayer(text("Farms"))));
    }
}
