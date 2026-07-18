package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.util.CommandGenerator.ListOptions;
import _959.server_waypoint.util.CommandGenerator.ListTarget;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

import static _959.server_waypoint.util.CommandGenerator.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class TextButton {
    private static final String REPLACE_SYMBOL = "⇄";
    private static final String RESTORE_SYMBOL = "↓";
    private static final String REMOVE_SYMBOL = "❌";
    private static final String EDIT_SYMBOL = "📝";
    private static final String ADD_SYMBOL = "+";

    private static Component buildButton(NamedTextColor color, String command, String symbol, Component hoverText) {
        Style btnStyle = Style.style()
                .decoration(TextDecoration.BOLD, TextDecoration.State.TRUE)
                .color(color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(hoverText))
                .build();
        return text("["+symbol+"]").style(btnStyle);
    }

    public static Component getListSortControls(ListTarget target, ListOptions options) {
        Component controls = translatable("waypoint.list.sort.label", NamedTextColor.GRAY);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            boolean selected = options.sortMode() == sortMode;
            controls = controls.appendSpace().append(listSortButton(
                    translatable(sortModeTranslationKey(sortMode)),
                    listSortCmd(target, options, sortMode),
                    selected,
                    true,
                    "button.sort." + sortMode.name().toLowerCase(Locale.ROOT)
            ));
        }

        boolean orderEnabled = options.sortMode() != WaypointSorting.SortMode.DEFAULT;
        controls = controls.appendSpace().append(text("·", NamedTextColor.GRAY)).appendSpace()
                .append(listSortButton(
                        text("↑"),
                        listOrderCmd(target, options, false),
                        orderEnabled && !options.reversed(),
                        orderEnabled,
                        "button.sort.ascending"
                ))
                .appendSpace()
                .append(listSortButton(
                        text("↓"),
                        listOrderCmd(target, options, true),
                        orderEnabled && options.reversed(),
                        orderEnabled,
                        "button.sort.descending"
                ));
        return controls.appendNewline()
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component listSortButton(
            Component label,
            String command,
            boolean selected,
            boolean enabled,
            String hoverTranslationKey
    ) {
        NamedTextColor color = !enabled
                ? NamedTextColor.DARK_GRAY
                : selected ? NamedTextColor.GOLD : NamedTextColor.AQUA;
        Component button = text("[")
                .append(label)
                .append(text("]"))
                .color(color)
                .decoration(TextDecoration.BOLD, selected)
                .decoration(TextDecoration.ITALIC, false);
        if (!enabled) {
            return button.hoverEvent(HoverEvent.showText(translatable("button.sort.order.unavailable")));
        }
        if (selected) {
            return button;
        }
        return button.clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(translatable(hoverTranslationKey)));
    }

    private static String sortModeTranslationKey(WaypointSorting.SortMode sortMode) {
        return switch (sortMode) {
            case DEFAULT -> "waypoint.sort.default";
            case NAME -> "waypoint.sort.name";
            case DISTANCE -> "waypoint.sort.distance";
            case COLOR -> "waypoint.sort.color";
        };
    }

    public static Component getPageNavigation(
            ListTarget target,
            ListOptions options,
            int totalPages,
            int totalWaypoints
    ) {
        Component previous = options.pageNumber() > 1
                ? pageButton(
                        "←",
                        listPageCmd(target, options, options.pageNumber() - 1),
                        "button.page.previous"
                )
                : text("[←]", NamedTextColor.DARK_GRAY);
        Component next = options.pageNumber() < totalPages
                ? pageButton(
                        "→",
                        listPageCmd(target, options, options.pageNumber() + 1),
                        "button.page.next"
                )
                : text("[→]", NamedTextColor.DARK_GRAY);
        Component pageText = translatable(
                "waypoint.list.page",
                text(options.pageNumber()),
                text(totalPages),
                text(options.pageLimit()),
                text(totalWaypoints)
        ).color(NamedTextColor.GRAY);
        return previous.appendSpace().append(pageText).appendSpace().append(next)
                .decoration(TextDecoration.BOLD, false);
    }

    private static Component pageButton(String symbol, String command, String hoverTranslationKey) {
        return text("[" + symbol + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(translatable(hoverTranslationKey)));
    }

    public static Component replaceButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildButton(
                NamedTextColor.AQUA,
                editCmd(dimensionName, listName, waypoint.name(), waypoint),
                REPLACE_SYMBOL,
                Component.translatable("button.replace")
        );
    }

    public static Component restoreButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildButton(
                NamedTextColor.LIGHT_PURPLE,
                addCmd(dimensionName, listName, waypoint),
                RESTORE_SYMBOL,
                Component.translatable("button.restore")
        );
    }

    public static Component removeButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildButton(
                NamedTextColor.RED,
                removeCmd(dimensionName, listName, waypoint),
                REMOVE_SYMBOL,
                Component.translatable("button.remove")
        );
    }

    public static Component editButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildButton(
                NamedTextColor.YELLOW,
                editCmd(dimensionName, listName, waypoint.name(), waypoint),
                EDIT_SYMBOL,
                Component.translatable("button.edit")
        );
    }

    public static Component addWaypointButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildButton(
                NamedTextColor.GREEN,
                addCmd(dimensionName, listName, waypoint),
                ADD_SYMBOL,
                Component.translatable("button.add.waypoint")
        );
    }

    public static Component addListButton(String dimensionName, String listName) {
        return buildButton(
                NamedTextColor.GREEN,
                addListCmd(dimensionName, listName),
                ADD_SYMBOL,
                Component.translatable("button.add.list")
        );
    }
}
