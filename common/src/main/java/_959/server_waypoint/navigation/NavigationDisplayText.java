package _959.server_waypoint.navigation;

import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static _959.server_waypoint.text.FormattedTextHelper.parse;

/**
 * Builds shared navigation text without retaining platform player or display
 * objects.
 */
public final class NavigationDisplayText {
    private static final double STRAIGHT_AHEAD_THRESHOLD_DEGREES = 0.5D;
    public static final String SYMBOL_HIGH = "⏶";
    public static final String SYMBOL_LOW = "⏷";
    public static final String SYMBOL_RIGHT = "⏵";
    public static final String SYMBOL_LEFT = "⏴";
    public static final String SYMBOL_SAME_LEVEL = "⏺";
    public static final String SYMBOL_FORWARD = "↑";

    private NavigationDisplayText() {
    }

    public static Component buildItemName(NavigationTarget target) {
        Component name = target.waypointDisplayName().isEmpty()
                || target.waypointDisplayName().equals(target.waypointName())
                ? text(target.waypointName()) : parse(target.waypointDisplayName());
        return name
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                .colorIfAbsent(TextColor.color(target.rgb()));
    }

    /** The navigation item's first tooltip line, with independently styled list and waypoint names. */
    public static Component buildItemTooltipName(NavigationTarget target) {
        return empty().decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                .append(text(target.listName(), NamedTextColor.WHITE))
                .append(text(" › ", NamedTextColor.WHITE))
                .append(buildItemName(target));
    }

    public static List<Component> buildItemLore(NavigationTarget target) {
        List<Component> lore = new ArrayList<>();
        lore.add(dimensionName(target).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (!target.waypointKeywords().isEmpty()) {
            lore.add(text(String.join(", ", target.waypointKeywords()), TextColor.color(0x447FFF))
                    .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        if (!target.waypointDescription().isEmpty()) {
            lore.addAll(splitLoreLines(parse(target.waypointDescription())
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        }
        return List.copyOf(lore);
    }

    /** Item lore needs separate components for newlines, retaining each component's inherited style. */
    private static List<Component> splitLoreLines(Component component) {
        List<Component> lines = new ArrayList<>();
        if (component instanceof TextComponent textComponent) {
            for (String part : textComponent.content().split("\\R|\\\\n", -1)) {
                lines.add(textComponent.content(part).children(List.of()));
            }
        } else {
            lines.add(component.children(List.of()));
        }
        for (Component child : component.children()) {
            List<Component> childLines = splitLoreLines(child);
            int lastLine = lines.size() - 1;
            lines.set(lastLine, lines.get(lastLine).append(childLines.get(0)));
            for (int index = 1; index < childLines.size(); index++) {
                lines.add(empty().style(component.style()).append(childLines.get(index)));
            }
        }
        return lines;
    }

    /** The dimension ID in its colour for item lore. */
    private static Component dimensionName(NavigationTarget target) {
        return text(target.dimensionName()).color(DimensionStyle.colorOf(target.dimensionName()));
    }

    public static Component build(NavigationSession session, NavigationSnapshot snapshot) {
        NavigationTarget target = session.target();
        Component targetName = buildItemName(target);
        if (!snapshot.inTargetDimension()) {
            return empty()
                    .append(targetName)
                    .append(text(" — "))
                    .append(translatable(
                            "waypoint.navigation.wrong_dimension",
                            DimensionStyle.displayName(target.dimensionName())
                                    .color(DimensionStyle.colorOf(target.dimensionName()))
                    ));
        }

        long distance = Math.round(snapshot.horizontalDistance());
        return empty()
                .append(targetName)
                .append(text(" — "))
                .append(turnIndicator(snapshot.signedTurnAngle()))
                .append(text(" | "))
                .append(meters(distance))
                .append(text(" "))
                .append(verticalDifference(snapshot.verticalDifference()));
    }

    public static Component buildTextDisplay(
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        NavigationTarget target = session.target();
        Component targetName = buildItemName(target);
        if (!snapshot.inTargetDimension()) {
            return empty()
                    .append(targetName)
                    .append(newline())
                    .append(translatable(
                            "waypoint.navigation.wrong_dimension",
                            DimensionStyle.displayName(target.dimensionName())
                                    .color(DimensionStyle.colorOf(target.dimensionName()))
                    ));
        }

        long distance = Math.round(snapshot.horizontalDistance());
        return empty()
                .append(targetName)
                .append(text(" — "))
                .append(turnIndicator(snapshot.signedTurnAngle()))
                .append(newline())
                .append(meters(distance))
                .append(text(" | "))
                .append(verticalDifference(snapshot.verticalDifference()));
    }

    private static Component turnIndicator(double signedTurn) {
        if (Math.abs(signedTurn) < STRAIGHT_AHEAD_THRESHOLD_DEGREES) {
            return text(SYMBOL_FORWARD);
        }
        String symbol = signedTurn < 0.0D ? SYMBOL_LEFT : SYMBOL_RIGHT;
        return text(symbol + Math.round(Math.abs(signedTurn)) + "°");
    }

    private static Component verticalDifference(double signedDifference) {
        long difference = Math.round(Math.abs(signedDifference));
        String symbol = difference == 0L ? SYMBOL_SAME_LEVEL : signedDifference > 0.0D ? SYMBOL_HIGH : SYMBOL_LOW;
        return text(symbol).append(meters(difference));
    }

    private static Component meters(long distance) {
        return translatable("waypoint.navigation.unit.meter", text(distance));
    }
}
