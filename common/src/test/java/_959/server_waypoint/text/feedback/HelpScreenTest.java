package _959.server_waypoint.text.feedback;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ChatFont;
import _959.server_waypoint.translation.TranslationFilesTest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.translation.Translator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
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
                "Commands  List · Details · Add · Edit · Remove",
                "  Teleport · Navigate · Upload · Download · Remote",
                "  Reload"), lines(index));
        assertEquals("/wp", clickOf(index, "open the menu"));
        assertEquals(List.of("/wp", "/wp help list", "/wp help details", "/wp help add", "/wp help edit", "/wp help remove",
                "/wp help tp", "/wp help navigate", "/wp help upload", "/wp help download", "/wp help remote",
                "/wp help reload"), runCommands(index));
        assertEquals("Add commands\nUsage and examples", tooltipOf(index, "Add"));
        assertEquals("Commands  List · Details · Navigate · Download", lines(HelpScreen.index(Fixtures.member())).get(2));
    }

    @Test
    void plainTextViewersGetTheTopicIdsToType() {
        Component index = HelpScreen.index(Fixtures.console());

        assertEquals(List.of(
                "Server Waypoint help",
                "Run /wp help <topic> for usage and examples.",
                "Commands  list · details · add · edit · remove · tp · navigate · upload · download · remote · reload"),
                lines(index));
        assertEquals(NamedTextColor.AQUA, colorOf(index, "add"));
        assertEquals(NamedTextColor.AQUA, colorOf(index, "/wp help <topic>"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(index, "<topic>"));
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
        assertEquals(NamedTextColor.GREEN, colorOf(add, "<dimension>"));
        assertEquals(NamedTextColor.GRAY, colorOf(add, "[<initials>"));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<initials>"));
        assertEquals(NamedTextColor.AQUA, colorOf(add, "/wp add <position>"));
        assertTrue(suggestions(add).contains("/wp add ~ ~ ~ "));
        assertEquals("Create an empty list\nQuote names with spaces\nClick to fill it in", tooltipOf(add, "/wp add <dimension> <list>"));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"", clickOf(add, "  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        ChatAssert.assertFitsChat(add);
    }

    @Test
    void keywordsInOptionalPartsAreAquaAndOnlyTheirBracketsAndBarsAreGray() {
        Component list = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.LIST, false);
        Component upload = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.UPLOAD, false);

        assertEquals(NamedTextColor.GRAY, colorOf(list, "[search"));
        assertEquals(NamedTextColor.AQUA, colorOf(list, "search <text>"));
        assertEquals(NamedTextColor.YELLOW, colorOf(list, "<text>"));
        assertEquals(NamedTextColor.AQUA, colorOf(list, "order <direction>"));
        assertEquals(NamedTextColor.AQUA, colorOf(upload, "force server"));
        assertEquals(NamedTextColor.GRAY, colorOf(upload, "|local"));
        assertEquals(NamedTextColor.AQUA, colorOf(upload, "local [delete"));
    }

    @Test
    void remoteDimensionHelpExplainsIdentifierSyntaxAndDefaultNamespace() {
        Component remote = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.REMOTE, false);
        String tooltip = tooltipOf(remote, "/wp remote details <server> <dimension> <list>");
        assertTrue(tooltip.contains("namespace:path"), tooltip);
        assertTrue(tooltip.contains("minecraft"), tooltip);
        assertTrue(tooltip.contains("without quotes"), tooltip);
        assertEquals(NamedTextColor.GREEN, colorOf(remote, "<dimension>"));
    }

    @Test
    void argumentsAreColouredByTheirType() {
        Component add = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.ADD, false);
        Component list = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.LIST, false);

        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<list>"));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<name>"));
        assertEquals(NamedTextColor.GREEN, colorOf(add, "<dimension>"));
        assertEquals(NamedTextColor.GREEN, colorOf(add, "<id>"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, colorOf(add, "<position>"));
        assertEquals(NamedTextColor.GOLD, colorOf(add, "<yaw>"));
        assertEquals(NamedTextColor.GOLD, colorOf(list, "<number>"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(add, "<color>"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(add, "<global>"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(list, "<view>"));
        assertEquals(NamedTextColor.DARK_PURPLE,
                colorOf(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, false), "<method>"));
        assertEquals(NamedTextColor.GOLD, colorOf(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, true), "<x>"));
    }

    @Test
    void listOptionsComeAfterOnlyTheUsagesThatTakeThem() {
        List<String> lines = lines(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.LIST, false));
        int options = lines.indexOf("  Options, in this order, after any of the above");

        assertTrue(lines.indexOf("/wp list all") < options, String.join("\n", lines));
        assertTrue(options < lines.indexOf("/wp list dimensions [page <number>]"), String.join("\n", lines));
    }

    @Test
    void reloadHelpSaysWhetherEachConfigSettingReloads() {
        String help = render(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.RELOAD, false));
        List<String> unnamed = new ArrayList<>();
        for (Map.Entry<String, JsonElement> setting : new Gson().toJsonTree(new Config()).getAsJsonObject().entrySet()) {
            if (help.contains(setting.getKey())) {
                continue;
            }
            if (!setting.getValue().isJsonObject()) {
                unnamed.add(setting.getKey());
                continue;
            }
            for (String child : setting.getValue().getAsJsonObject().keySet()) {
                if (!help.contains(child)) {
                    unnamed.add(setting.getKey() + "." + child);
                }
            }
        }

        assertEquals(List.of(), unnamed, help);
    }

    @Test
    void remoteHelpCoversDetails() {
        assertTrue(lines(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.REMOTE, false))
                .contains("/wp remote details <server> <dimension> <list> <waypoint>"));
    }

    @Test
    void notesNameTheirArgumentInTheColourOfItsType() {
        Component list = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.LIST, false);
        Component tooltip = (Component) Objects.requireNonNull(find(list, "[search").style().hoverEvent()).value();

        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(tooltip, "<mode>"));
        assertEquals(NamedTextColor.GRAY, colorOf(tooltip, ": default, name, distance or color"));
        assertEquals(NamedTextColor.DARK_PURPLE,
                colorOf(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.LIST, false), "<mode>: default"));
    }

    @Test
    void examplesColourEachValueLikeTheArgumentItFills() {
        String command = "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" set color gold";
        Component edit = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.EDIT, false);
        Component plain = HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.EDIT, false);

        assertEquals(NamedTextColor.AQUA, colorOf(edit, "set"));
        assertEquals(NamedTextColor.YELLOW, colorOf(edit, "gold"));
        assertEquals(command, clickOf(edit, "gold"));
        assertEquals(NamedTextColor.AQUA, colorOf(plain, "  /wp edit waypoint minecraft:overworld"));
        assertEquals(NamedTextColor.GREEN, colorOf(plain, "minecraft:overworld \"Home Bases\""));
        assertEquals(NamedTextColor.YELLOW, colorOf(plain, "\"Home Bases\" \"Main Home\""));
        assertEquals(NamedTextColor.AQUA, colorOf(plain, "set color gold"));
        assertEquals(NamedTextColor.DARK_PURPLE, colorOf(plain, "color gold"));
        assertEquals(NamedTextColor.LIGHT_PURPLE,
                colorOf(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false), "~ ~ ~"));
    }

    @Test
    void everyTopicFitsTheChat() {
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            ChatAssert.assertFitsChat(HelpScreen.topic(Fixtures.player(), topic, true));
        }
    }

    @Test
    void everyTopicTitleFitsTheChatInEveryLocale() {
        List<String> problems = new ArrayList<>();
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            Component help = HelpScreen.topic(Fixtures.player(), topic, true);
            String english = lines(help).get(0);
            for (String locale : TranslationFilesTest.LOCALES) {
                String title = lines(help, Translator.parseLocale(locale)).get(0);
                if (!locale.equals("en_us") && title.equals(english)) {
                    problems.add(locale + " falls back to English: " + title);
                }
                int width = ChatFont.width(title);
                if (width > ChatFont.CHAT_WIDTH) {
                    problems.add(locale + ", " + width + " px: " + title);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
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
    void plainTextTopicsKeepTheColoursOfChatAndItsTooltips() {
        Component add = HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false);

        assertEquals(NamedTextColor.AQUA, colorOf(add, "/wp add <dimension> <list>"));
        assertEquals(NamedTextColor.GREEN, colorOf(add, "<dimension>"));
        assertEquals(NamedTextColor.GRAY, colorOf(add, "[<initials>"));
        assertEquals(NamedTextColor.WHITE, colorOf(add, "Create an empty list"));
        assertEquals(NamedTextColor.GRAY, colorOf(add, "Quote names with spaces"));
        assertEquals(NamedTextColor.AQUA, colorOf(add, "  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        assertEquals(NamedTextColor.WHITE, colorOf(add, "Add Main Home where you stand"));
    }

    @Test
    void navigateHelpMentionsTheTextDisplayOnlyWhereItIsSupported() {
        List<String> supported = lines(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.NAVIGATE, true));

        assertFalse(render(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, false)).contains("text_display"));
        assertTrue(supported.contains("/wp navigate config text_display"));
        assertTrue(supported.contains("/wp navigate config text_display transformation translation|rotation|scale <x> <y> <z>"));
        assertTrue(supported.contains("/wp navigate config text_display transformation reset"));
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
