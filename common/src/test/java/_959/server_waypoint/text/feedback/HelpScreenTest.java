package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ChatFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpScreenTest {
    @Test
    void theIndexLinksTheMenuAndEveryTopicTheViewerCanUse() {
        Component index = HelpScreen.index(Fixtures.player());

        assertEquals(List.of(
                "Server Waypoint help",
                "Most things are a click away: open the menu.",
                "Commands  List · Add · Edit · Remove · Teleport",
                "  Navigate · Upload · Download · Remote"), lines(index));
        assertEquals("/wp", clickOf(index, "open the menu"));
        assertEquals(List.of("/wp", "/wp help list", "/wp help add", "/wp help edit", "/wp help remove", "/wp help tp",
                "/wp help navigate", "/wp help upload", "/wp help download", "/wp help remote"), runCommands(index));
        assertEquals("Add commands\nUsage and examples", tooltipOf(index, "Add"));
        assertEquals("Commands  List · Navigate · Download", lines(HelpScreen.index(Fixtures.member())).get(2));
    }

    @Test
    void plainTextViewersGetTheTopicIdsToType() {
        assertEquals(List.of(
                "Server Waypoint help",
                "Run /wp help <topic> for usage and examples.",
                "Commands  list · add · edit · remove · tp · navigate · upload · download · remote"),
                lines(HelpScreen.index(Fixtures.console())));
    }

    @Test
    void usagesAreColouredBrokenToFitAndExplainedInTheirTooltips() {
        Component add = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.ADD, false);
        List<String> lines = lines(add);

        assertEquals("Add  hover a line for details, click to use it", lines.get(0));
        assertEquals("/wp add", lines.get(1));
        assertEquals("/wp add <dimension> <list>", lines.get(2));
        assertTrue(lines.get(4).startsWith("    "), lines.get(4));
        assertEquals("Help index · Menu", lines.get(lines.size() - 1));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<dimension>"));
        assertEquals(NamedTextColor.GRAY, colorOf(add, "[<initials>"));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<initials>"));
        assertEquals(NamedTextColor.AQUA, colorOf(add, "/wp add <position>"));
        assertTrue(suggestions(add).contains("/wp add ~ ~ ~ "));
        assertEquals("Create an empty list\nQuote names with spaces\nClick to fill it in", tooltipOf(add, "/wp add <dimension> <list>"));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"", clickOf(add, "  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        ChatAssert.assertFitsChat(add);
    }

    @Test
    void everyTopicFitsTheChat() {
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            ChatAssert.assertFitsChat(HelpScreen.topic(Fixtures.player(), topic, true));
        }
    }

    @Test
    void plainTextTopicsPrintTheirTooltipsAsIndentedLines() {
        List<String> lines = lines(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false));

        assertEquals("Add", lines.get(0));
        assertTrue(lines.contains("/wp add <dimension> <list>"));
        assertTrue(lines.contains("  Create an empty list"));
        assertTrue(lines.contains("  Quote names with spaces"));
        assertTrue(lines.contains("  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        assertTrue(lines.contains("    Add Main Home where you stand"));
        assertFalse(render(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false)).contains("Help index"));
    }

    @Test
    void navigateHelpMentionsTheTextDisplayOnlyWhereItIsSupported() {
        assertFalse(render(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, false)).contains("text_display"));
        assertTrue(render(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, true)).contains("/wp navigate config text_display"));
    }

    @Test
    void wrappingBreaksAtSpacesAndIndentsContinuations() {
        List<String> wrapped = HelpScreen.wrap("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]", "", "    ");

        assertTrue(wrapped.size() > 1);
        assertTrue(wrapped.stream().allMatch(line -> ChatFont.width(line) <= ChatFont.CHAT_WIDTH));
        assertTrue(wrapped.get(1).startsWith("    "));
        assertEquals("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]",
                String.join(" ", wrapped.stream().map(String::trim).toList()));
    }
}
