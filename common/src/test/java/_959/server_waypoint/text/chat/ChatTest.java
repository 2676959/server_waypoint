package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTest {
    static final Viewer PLAYER = new Viewer(Viewer.everything(), true, false,
            "minecraft:overworld", new WaypointPos(100, 64, -20), 37F);
    static final Viewer CONSOLE = new Viewer(Viewer.everything(), false, true,
            "minecraft:overworld", new WaypointPos(0, 64, 0), 0F);

    @Test
    void separatorsAreDarkGrayPiecesOfTheirOwnAndNullPiecesAreLeftOut() {
        Component joined = Chat.join(text("Lists"), null, text("Tree"));

        assertEquals("Lists · Tree", render(joined));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(joined, " · "));
    }

    @Test
    void linksCarryTheirClickAndTooltipForPlayers() {
        Component link = Chat.link(PLAYER, text("Help"), NamedTextColor.GRAY, Click.run("/wp help"),
                Tooltip.of(text("Open help")).hint("wp.hint.open"));

        assertEquals("/wp help", clickOf(link, "Help"));
        assertEquals("Open help\nClick to open", tooltipOf(link, "Help"));
        assertEquals(NamedTextColor.GRAY, colorOf(link, "Help"));
    }

    @Test
    void plainTextViewersKeepLinkLabelsButNotControlsOrButtons() {
        Component line = Chat.join(
                Chat.link(CONSOLE, text("Farms"), NamedTextColor.WHITE, Click.run("/wp list"), null),
                Chat.control(CONSOLE, text("All"), NamedTextColor.AQUA, Click.run("/wp list all"), null),
                Chat.button(CONSOLE, text("Back"), NamedTextColor.GRAY, Click.run("/wp"), null));

        assertEquals("Farms", render(line));
        assertTrue(runCommands(line).isEmpty());
    }

    @Test
    void commandsOver256CharactersLoseTheirClickAndTheirClickHint() {
        String command = "/wp list " + "x".repeat(300);
        Component link = Chat.link(PLAYER, text("Farms"), NamedTextColor.WHITE, Click.run(command),
                Tooltip.of(text("Farms")).hint("wp.hint.open"));

        assertNull(clickOf(link, "Farms"));
        assertEquals("Farms", tooltipOf(link, "Farms"));
        assertFalse(Click.run(command).fits());
        assertNull(Click.suggest(command).event());
    }

    @Test
    void buttonsAreBracketedAndUndecorated() {
        Component button = Chat.button(PLAYER, text("Back"), NamedTextColor.GRAY, Click.run("/wp"), null);

        assertEquals("[Back]", render(button));
        assertEquals("/wp", clickOf(button, "Back"));
    }

    @Test
    void resultsAndErrorsStartWithTheirGlyphInTheirColour() {
        Component ok = Chat.ok(text("Saved"), List.of(text("Undo")));
        Component error = Chat.error(text("Missing."), text("Browse"));

        assertEquals("✔ Saved   Undo", render(ok));
        assertEquals(NamedTextColor.GREEN, colorOf(ok, "✔ "));
        assertEquals("✘ Missing. Browse", render(error));
        assertEquals(NamedTextColor.RED, colorOf(error, "Missing."));
        assertEquals("✔ Saved", render(Chat.ok(text("Saved"), java.util.Arrays.<Component>asList(null, null))));
    }

    @Test
    void theViewedAsLineIsGrayItalicsWithThePlayersNameInYellow() {
        Component line = Chat.viewedAs("Alex");

        assertEquals("Viewed as Alex", render(line));
        assertEquals(NamedTextColor.GRAY, colorOf(line, "Viewed as"));
        assertEquals(TextDecoration.State.TRUE, find(line, "Viewed as").style().decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.YELLOW, colorOf(line, "Alex"));
        assertEquals(TextDecoration.State.TRUE, find(line, "Alex").style().decoration(TextDecoration.ITALIC));
        assertEquals("Visto como Alex", render(line, Locale.forLanguageTag("es-ES")));
    }

    @Test
    void countsUseTheSingularOnlyForOne() {
        assertEquals("1 waypoint", render(Chat.count("wp.count.waypoint", 1)));
        assertEquals("0 waypoints", render(Chat.count("wp.count.waypoint", 0)));
        assertEquals("12 waypoints", render(Chat.count("wp.count.waypoint", 12)));
    }

    @Test
    void linesAreJoinedWithoutATrailingNewline() {
        ChatLines message = new ChatLines().add(text("one")).add(null).line(text("two "), null, text("three"));

        assertEquals(List.of("one", "two three"), lines(message.build()));
        assertEquals(2, message.size());
    }

    @Test
    void aScreenIsMarkedAndTheMarkDrawsNothing() {
        Component screen = Chat.screen(new ChatLines().add(text("Farms")).add(text("Iron Farm")).build());

        assertTrue(Chat.isScreen(screen));
        assertEquals(List.of("Farms", "Iron Farm"), lines(screen));
        assertFalse(Chat.isScreen(Chat.ok(text("Saved"))));
    }

    @Test
    void aScreenInsideALargerMessageStillMakesItAScreen() {
        Component message = new ChatLines().add(text("Click a name.")).add(Chat.screen(text("Farms"))).build();

        assertTrue(Chat.isScreen(message));
    }

    @Test
    void theMarkDoesNotSpillOntoTheScreensText() {
        Component screen = Chat.screen(Chat.colored(text("Farms"), NamedTextColor.WHITE));

        assertNull(find(screen, "Farms").style().insertion());
        assertEquals(NamedTextColor.WHITE, colorOf(screen, "Farms"));
    }

    @Test
    void tooltipsHaveAWhiteTitleGrayLinesAndAquaHints() {
        Component tooltip = Tooltip.of(text("Farms")).line(text("7 waypoints"), text("Overworld"))
                .hint("wp.hint.open").build();

        assertEquals("Farms\n7 waypoints · Overworld\nClick to open", render(tooltip));
        assertEquals(NamedTextColor.WHITE, colorOf(tooltip, "Farms"));
        assertEquals(NamedTextColor.GRAY, colorOf(tooltip, "7 waypoints"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(tooltip, " · "));
        assertEquals(NamedTextColor.AQUA, colorOf(tooltip, "Click to open"));
    }

    @Test
    void viewersKnowTheirPermissionsAndDimension() {
        Viewer member = new Viewer(Set.of(Viewer.Permission.NAVIGATE), false, false, "minecraft:the_nether", null, 0F);

        assertTrue(member.can(Viewer.Permission.NAVIGATE));
        assertFalse(member.can(Viewer.Permission.TP));
        assertTrue(member.isIn("minecraft:the_nether"));
        assertFalse(member.isIn("minecraft:overworld"));
    }
}
