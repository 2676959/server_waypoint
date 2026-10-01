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

import java.nio.file.Path;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
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
    void playersMessagesEndWithOneNewline() {
        assertEquals("Farms\n", render(PlatformMessageSender.forPlayer(text("Farms"))));
    }
}
