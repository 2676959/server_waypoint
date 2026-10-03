package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatFont;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** The help index and topics (spec 12). */
public final class HelpScreen {
    private static final int TOPICS_PER_LINE = 5;

    private HelpScreen() {
    }

    public static Component index(Viewer viewer) {
        ChatLines lines = new ChatLines().add(translatable("wp.help.title", GOLD));
        List<HelpTopics.Topic> topics = HelpTopics.readable(viewer);
        if (viewer.plainText()) {
            lines.add(translatable("wp.help.plain_hint", GRAY, colorize("/wp help <topic>")));
            lines.line(translatable("wp.help.commands", GRAY), text("  "),
                    Chat.join(topics.stream().map(topic -> (Component) text(topic.id(), AQUA)).toList()));
            return lines.buildScreen();
        }
        lines.add(translatable("wp.help.menu_hint", GRAY, Chat.link(viewer, translatable("wp.help.open_menu"), AQUA,
                Click.run("/wp"), Tooltip.of("wp.help.open_menu.tooltip"))));
        List<Component> links = topics.stream().map(topic -> Chat.link(viewer, topic.label(), AQUA,
                Click.run("/wp help " + topic.id()),
                Tooltip.of("wp.help.topic.tooltip", topic.label()).line("wp.help.topic.detail"))).toList();
        for (int start = 0; start < links.size(); start += TOPICS_PER_LINE) {
            Component row = Chat.join(links.subList(start, Math.min(start + TOPICS_PER_LINE, links.size())));
            if (start == 0) {
                lines.line(translatable("wp.help.commands", GRAY), text("  "), row);
            } else {
                lines.line(text("  "), row);
            }
        }
        return lines.buildScreen();
    }

    public static Component topic(Viewer viewer, HelpTopics.Topic topic, boolean textDisplay) {
        HelpTopics.Content content = HelpTopics.content(topic, textDisplay);
        ChatLines lines = new ChatLines();
        Component title = Chat.colored(topic.label(), GOLD);
        lines.add(viewer.plainText() ? title : Chat.concat(title, text("  "), translatable("wp.help.topic.hint", GRAY)));
        for (HelpTopics.Usage usage : content.usages()) {
            if (viewer.plainText()) {
                lines.add(colorize(usage.syntax()));
                for (Component line : usage.tooltip().textLines()) {
                    lines.line(text("  "), line);
                }
                continue;
            }
            Click click = Click.suggest(usage.suggestion());
            Tooltip tooltip = usage.tooltip().hint("wp.hint.fill");
            for (String line : wrap(usage.syntax(), "", "    ")) {
                lines.add(Chat.link(viewer, colorize(line), AQUA, click, tooltip));
            }
        }
        if (!content.examples().isEmpty()) {
            lines.add(translatable("wp.help.examples", GRAY));
        }
        for (HelpTopics.Example example : content.examples()) {
            List<@Nullable String> arguments = example.arguments();
            if (viewer.plainText()) {
                lines.add(Chat.colored(exampleLine("  " + example.command(), arguments, new int[]{0}), AQUA));
                lines.line(text("    "), translatable(example.descriptionKey(), WHITE));
                continue;
            }
            Tooltip tooltip = Tooltip.of(translatable(example.descriptionKey())).hint("wp.hint.fill");
            int[] word = {0};
            for (String line : wrap(example.command(), "  ", "      ")) {
                lines.add(Chat.link(viewer, exampleLine(line, arguments, word), AQUA, Click.suggest(example.command()),
                        tooltip));
            }
        }
        if (!viewer.plainText()) {
            lines.add(Chat.join(
                    Chat.link(viewer, translatable("wp.help.index"), GRAY, Click.run("/wp help"), Tooltip.of("wp.help.index.tooltip")),
                    Chat.link(viewer, translatable("wp.help.menu"), GRAY, Click.run("/wp"), Tooltip.of("wp.help.open_menu.tooltip"))));
        }
        return lines.buildScreen();
    }

    /** Breaks text at spaces so no line passes the chat width; later lines start with the continuation indent. */
    public static List<String> wrap(String text, String indent, String continuation) {
        List<String> lines = new ArrayList<>();
        String line = indent;
        boolean empty = true;
        for (String word : text.split(" ")) {
            String candidate = empty ? line + word : line + " " + word;
            if (!empty && ChatFont.width(candidate) > ChatFont.CHAT_WIDTH) {
                lines.add(line);
                line = continuation + word;
            } else {
                line = candidate;
            }
            empty = false;
        }
        lines.add(line);
        return lines;
    }

    /**
     * Commands and keywords aqua, each <argument> in the colour of its type, and the [brackets] and | of
     * optional parts gray.
     */
    private static Component colorize(String line) {
        List<Component> pieces = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        TextColor runColor = null;
        TextColor argument = null;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '<') {
                int end = line.indexOf('>', index);
                argument = HelpTopics.argumentColor(line.substring(index + 1, end < 0 ? line.length() : end));
            }
            TextColor color;
            if (argument != null) {
                color = argument;
            } else if (character == '[' || character == ']' || character == '|') {
                color = GRAY;
            } else {
                color = AQUA;
            }
            if (character == '>') {
                argument = null;
            }
            if (runColor != null && !runColor.equals(color)) {
                pieces.add(text(run.toString(), runColor));
                run.setLength(0);
            }
            runColor = color;
            run.append(character);
        }
        if (runColor != null) {
            pieces.add(text(run.toString(), runColor));
        }
        return Chat.concat(pieces);
    }

    /**
     * A line of an example: its indent, then its words, each value in the colour of the argument it fills.
     * word counts the example's words across its lines.
     */
    private static Component exampleLine(String line, List<@Nullable String> arguments, int[] word) {
        String words = line.stripLeading();
        List<Component> pieces = new ArrayList<>();
        pieces.add(text(line.substring(0, line.length() - words.length())));
        for (String piece : words.split(" ")) {
            if (pieces.size() > 1) {
                pieces.add(text(" "));
            }
            String argument = arguments.get(word[0]++);
            pieces.add(argument == null ? text(piece) : text(piece, HelpTopics.argumentColor(argument)));
        }
        return Chat.concat(pieces);
    }
}
