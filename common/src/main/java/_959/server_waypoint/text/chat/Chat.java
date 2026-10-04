package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The pieces chat messages are made of. Every piece is its own component under a neutral parent,
 * so a click or tooltip never spills onto the text next to it. Pieces that only work by clicking
 * return null for plain-text viewers; joining and concatenating leave null pieces out.
 */
public final class Chat {
    public static final Component SEPARATOR = Component.text(" · ", NamedTextColor.DARK_GRAY);
    public static final Component CRUMB = Component.text(" › ", NamedTextColor.DARK_GRAY);
    public static final String CHECK = "✔";
    public static final String CROSS = "✘";
    public static final String ELLIPSIS = "…";
    public static final String PICKER = "⏷";
    public static final String DOT = "●";
    private static final String SCREEN_INSERTION = "server_waypoint:screen";
    private static final Component SCREEN_MARK = Component.empty().insertion(SCREEN_INSERTION);

    private Chat() {
    }

    public static Component concat(@Nullable Component... pieces) {
        return concat(Arrays.asList(pieces));
    }

    public static Component concat(List<? extends @Nullable Component> pieces) {
        Component result = Component.empty();
        for (Component piece : pieces) {
            if (piece != null) {
                result = result.append(piece);
            }
        }
        return result;
    }

    public static Component join(@Nullable Component... pieces) {
        return join(Arrays.asList(pieces));
    }

    /** The pieces with a dark gray separator between each two. */
    public static Component join(List<? extends @Nullable Component> pieces) {
        return joinWith(pieces, SEPARATOR);
    }

    /** The pieces one space apart, the way [buttons] stand. */
    public static Component spaced(List<? extends @Nullable Component> pieces) {
        return joinWith(pieces, Component.space());
    }

    private static Component joinWith(List<? extends @Nullable Component> pieces, Component between) {
        Component result = Component.empty();
        boolean first = true;
        for (Component piece : pieces) {
            if (piece == null) {
                continue;
            }
            if (!first) {
                result = result.append(between);
            }
            result = result.append(piece);
            first = false;
        }
        return result;
    }

    /** True when every piece was left out. */
    public static boolean isEmpty(List<? extends @Nullable Component> pieces) {
        return pieces.stream().allMatch(piece -> piece == null);
    }

    /** The message with every matching piece left out, along with its children, translation arguments included. */
    public static Component without(Component message, Predicate<? super Component> leaveOut) {
        if (leaveOut.test(message)) {
            return Component.empty();
        }
        Component result = message;
        if (result instanceof TranslatableComponent translatable) {
            result = translatable.arguments(translatable.arguments().stream()
                    .map(argument -> argument.value() instanceof Component value
                            ? TranslationArgument.component(without(value, leaveOut)) : argument)
                    .toList());
        }
        return result.children(result.children().stream().map(child -> without(child, leaveOut)).toList());
    }

    public static Component colored(Component content, TextColor color) {
        return Component.empty().color(color).append(content);
    }

    /**
     * A piece of text that names something; clicking it opens it. Plain-text viewers keep the
     * label without the click or the tooltip.
     */
    public static Component link(Viewer viewer, Component label, TextColor color, Click click,
                                 @Nullable Tooltip tooltip) {
        Component piece = colored(label, color);
        if (viewer.plainText()) {
            return piece;
        }
        ClickEvent event = click.event();
        if (event != null) {
            piece = piece.clickEvent(event);
        }
        Tooltip shown = tooltip == null ? null : event == null ? tooltip.withoutHints() : tooltip;
        return shown == null ? piece : piece.hoverEvent(HoverEvent.showText(shown.build()));
    }

    /** A link that only works by clicking, such as All or Search. Plain-text viewers don't get it. */
    public static @Nullable Component control(Viewer viewer, Component label, TextColor color, Click click,
                                              @Nullable Tooltip tooltip) {
        return viewer.plainText() ? null : link(viewer, label, color, click, tooltip);
    }

    /** A [button]. Plain-text viewers don't get it. */
    public static @Nullable Component button(Viewer viewer, Component label, TextColor color, Click click,
                                             @Nullable Tooltip tooltip) {
        return control(viewer, concat(Component.text("["), label, Component.text("]")), color, click, tooltip);
    }

    /** A dark gray [button] that does nothing, with a tooltip saying why. */
    public static @Nullable Component disabledButton(Viewer viewer, Component label, Tooltip tooltip) {
        if (viewer.plainText()) {
            return null;
        }
        return hover(viewer, colored(concat(Component.text("["), label, Component.text("]")),
                NamedTextColor.DARK_GRAY), tooltip);
    }

    /** Text with a tooltip and no click. Plain-text viewers get the text alone. */
    public static Component hover(Viewer viewer, Component content, @Nullable Tooltip tooltip) {
        if (viewer.plainText() || tooltip == null) {
            return content;
        }
        return Component.empty().append(content).hoverEvent(HoverEvent.showText(tooltip.build()));
    }

    /** A green result line starting with ✔. */
    public static Component ok(Component message) {
        return colored(concat(Component.text(CHECK + " "), message), NamedTextColor.GREEN);
    }

    /** A result line followed, three spaces later, by its actions. */
    public static Component ok(Component message, List<? extends @Nullable Component> actions) {
        if (isEmpty(actions)) {
            return ok(message);
        }
        return concat(ok(message), Component.text("   "), join(actions));
    }

    /** A red error line starting with ✘. */
    public static Component error(Component message) {
        return colored(concat(Component.text(CROSS + " "), message), NamedTextColor.RED);
    }

    /** An error line followed by the link that helps recover from it. */
    public static Component error(Component message, @Nullable Component recovery) {
        return recovery == null ? error(message) : concat(error(message), Component.space(), recovery);
    }

    /**
     * "Viewed as Alex", gray and italic with the name in yellow, like vanilla's notices about
     * someone else's command: the first line of the copy a commander gets when /execute as shows
     * the feedback as Alex's.
     */
    public static Component viewedAs(String playerName) {
        return Component.empty().color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
                .append(Component.translatable("wp.viewed_as", Component.text(playerName).color(NamedTextColor.YELLOW)));
    }

    /** "1 waypoint" or "12 waypoints": the key's .one or .other form with the number as {0}. */
    public static Component count(String key, int count) {
        return Component.translatable(key + (count == 1 ? ".one" : ".other"), Component.text(count));
    }

    /**
     * Marks a message as a screen: structured feedback such as the menu, a list or a picker, which players
     * read apart from the next message. The platforms end a screen with a blank line for players; results,
     * errors, broadcasts and prompts are not screens and get none. The mark is an empty piece after the
     * screen. It draws nothing and, as a sibling, never touches the style of the screen's own text.
     */
    public static Component screen(Component screen) {
        return Component.empty().append(screen).append(SCREEN_MARK);
    }

    /**
     * Whether the message is a screen or has one among its pieces, such as a hint above a list. A screen
     * inside a tooltip or a translation argument doesn't count.
     */
    public static boolean isScreen(Component message) {
        if (SCREEN_INSERTION.equals(message.style().insertion())) {
            return true;
        }
        for (Component child : message.children()) {
            if (isScreen(child)) {
                return true;
            }
        }
        return false;
    }
}
