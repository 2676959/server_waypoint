package _959.server_waypoint.text.chat;

import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Renders messages, in English unless given a locale, and finds the style of the pieces a player sees. */
public final class ChatAssert {
    private static boolean installed;

    private ChatAssert() {
    }

    public record Run(String text, Style style) {
    }

    private static synchronized void install() {
        if (!installed) {
            new LanguageFilesManager(Path.of("build", "chat-assert"));
            GlobalTranslator.translator().addSource(new AdventureTranslator());
            installed = true;
        }
    }

    /** The visible pieces in order, with the style each one ends up with. */
    public static List<Run> runs(Component component) {
        return runs(component, Locale.US);
    }

    public static List<Run> runs(Component component, Locale locale) {
        install();
        List<Run> runs = new ArrayList<>();
        collect(GlobalTranslator.render(component, locale), Style.empty(), runs);
        return runs;
    }

    private static void collect(Component component, Style inherited, List<Run> runs) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            runs.add(new Run(text.content(), style));
        } else if (component instanceof TranslatableComponent translatable) {
            runs.add(new Run("<missing " + translatable.key() + ">", style));
        }
        for (Component child : component.children()) {
            collect(child, style, runs);
        }
    }

    public static String render(Component component) {
        return render(component, Locale.US);
    }

    public static String render(Component component, Locale locale) {
        return runs(component, locale).stream().map(Run::text).collect(Collectors.joining());
    }

    public static List<String> lines(Component component) {
        return lines(component, Locale.US);
    }

    public static List<String> lines(Component component, Locale locale) {
        return Arrays.asList(render(component, locale).split("\n", -1));
    }

    /**
     * The first place where this text starts at the beginning of a piece; it may run on into the
     * following pieces, since a translation with arguments renders as several pieces. The result
     * carries the style of the piece it starts in.
     */
    public static Run find(Component component, String text) {
        List<Run> runs = runs(component);
        String all = runs.stream().map(Run::text).collect(Collectors.joining());
        int offset = 0;
        for (Run run : runs) {
            if (all.startsWith(text, offset)) {
                return new Run(text, run.style());
            }
            offset += run.text().length();
        }
        throw new AssertionError("No piece starts with \"" + text + "\" in "
                + runs.stream().map(run -> "\"" + run.text() + "\"").collect(Collectors.joining(", ")));
    }

    public static @Nullable TextColor colorOf(Component component, String text) {
        return find(component, text).style().color();
    }

    public static @Nullable String clickOf(Component component, String text) {
        ClickEvent click = find(component, text).style().clickEvent();
        return click == null ? null : click.value();
    }

    public static @Nullable String tooltipOf(Component component, String text) {
        HoverEvent<?> hover = find(component, text).style().hoverEvent();
        return hover == null ? null : render((Component) hover.value());
    }

    /**
     * The chat limits of spec 1: no line over 320 px, at most 20 lines, or 19 for a screen so the
     * trailing blank line players get still fits the 20-line window, no bold, only vanilla glyphs and
     * no trailing newline.
     */
    public static void assertFitsChat(Component message) {
        List<String> problems = fitProblems(message, Locale.US);
        if (!problems.isEmpty()) {
            throw new AssertionError(String.join("\n", problems) + "\n--- message ---\n" + render(message));
        }
    }

    /**
     * What keeps the message from fitting chat for a viewer who reads it in this locale: the limits of
     * {@link #assertFitsChat(Component)}, except that only English is held to the vanilla glyphs, since
     * the Chinese locales cannot avoid Unifont.
     */
    public static List<String> fitProblems(Component message, Locale locale) {
        List<String> problems = new ArrayList<>();
        String text = render(message, locale);
        if (text.endsWith("\n")) {
            problems.add("ends with a newline");
        }
        List<String> lines = Arrays.asList(text.split("\n", -1));
        for (String line : lines) {
            int width = ChatFont.width(line);
            if (width > ChatFont.CHAT_WIDTH) {
                problems.add(width + " px: " + line);
            }
            if (locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                line.codePoints().filter(codePoint -> !ChatFont.isVanillaGlyph(codePoint)).forEach(codePoint ->
                        problems.add("not in the vanilla font: " + new String(Character.toChars(codePoint)) + " in " + line));
            }
        }
        if (Chat.isScreen(message)) {
            if (lines.size() + 1 > ChatFont.CHAT_LINES) {
                problems.add((lines.size() + 1) + " lines with the trailing blank line");
            }
        } else if (lines.size() > ChatFont.CHAT_LINES) {
            problems.add(lines.size() + " lines");
        }
        if (runs(message, locale).stream().anyMatch(run -> run.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)) {
            problems.add("bold text");
        }
        return problems;
    }

    public static List<String> runCommands(Component component) {
        return clicks(component, ClickEvent.Action.RUN_COMMAND);
    }

    public static List<String> suggestions(Component component) {
        return clicks(component, ClickEvent.Action.SUGGEST_COMMAND);
    }

    /** Clicks in order; the message is rendered first so clicks inside translation arguments count too. */
    private static List<String> clicks(Component component, ClickEvent.Action action) {
        install();
        List<String> commands = new ArrayList<>();
        collectClicks(GlobalTranslator.render(component, Locale.US), action, commands);
        return commands;
    }

    private static void collectClicks(Component component, ClickEvent.Action action, List<String> commands) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == action) {
            commands.add(click.value());
        }
        for (Component child : component.children()) {
            collectClicks(child, action, commands);
        }
    }
}
