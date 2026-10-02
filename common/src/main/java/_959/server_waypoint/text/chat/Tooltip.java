package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A hover text. The first line is white and names the object or the action, later lines are gray,
 * and click hints and typing instructions are aqua and come last. The pieces of a line are joined
 * with dark gray separators and keep their own colours.
 */
public final class Tooltip {
    private final Component title;
    private final List<Component> lines;
    private final List<Component> hints;

    private Tooltip(Component title, List<Component> lines, List<Component> hints) {
        this.title = title;
        this.lines = List.copyOf(lines);
        this.hints = List.copyOf(hints);
    }

    public static Tooltip of(Component title) {
        return new Tooltip(Objects.requireNonNull(title, "title"), List.of(), List.of());
    }

    public static Tooltip of(String key, ComponentLike... arguments) {
        return of(Component.translatable(key, arguments));
    }

    public Tooltip line(Component... pieces) {
        List<Component> lines = new ArrayList<>(this.lines);
        lines.add(Chat.join(pieces));
        return new Tooltip(this.title, lines, this.hints);
    }

    public Tooltip line(String key, ComponentLike... arguments) {
        return this.line(Component.translatable(key, arguments));
    }

    public Tooltip hint(String key) {
        List<Component> hints = new ArrayList<>(this.hints);
        hints.add(Component.translatable(key));
        return new Tooltip(this.title, this.lines, hints);
    }

    /** The same tooltip without its hints, for a control whose click was dropped. */
    public Tooltip withoutHints() {
        return new Tooltip(this.title, this.lines, List.of());
    }

    /**
     * The title and the lines in their tooltip colours, for plain-text viewers who read documentation as
     * indented lines.
     */
    public List<Component> textLines() {
        List<Component> text = new ArrayList<>();
        text.add(Chat.colored(this.title, NamedTextColor.WHITE));
        for (Component line : this.lines) {
            text.add(Chat.colored(line, NamedTextColor.GRAY));
        }
        return text;
    }

    public Component build() {
        Component tooltip = Component.empty().append(Chat.colored(this.title, NamedTextColor.WHITE));
        for (Component line : this.lines) {
            tooltip = tooltip.appendNewline().append(Chat.colored(line, NamedTextColor.GRAY));
        }
        for (Component hint : this.hints) {
            tooltip = tooltip.appendNewline().append(Chat.colored(hint, NamedTextColor.AQUA));
        }
        return tooltip;
    }
}
