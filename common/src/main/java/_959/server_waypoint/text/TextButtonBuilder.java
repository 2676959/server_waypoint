package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import _959.server_waypoint.util.StringCommandBuilder.ListTarget;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.util.StringCommandBuilder.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class TextButtonBuilder {
    private static final String REPLACE_SYMBOL = "⇄";
    private static final String RESTORE_SYMBOL = "↓";
    private static final String REMOVE_SYMBOL = "❌";
    private static final String EDIT_SYMBOL = "📝";
    private static final String ADD_SYMBOL = "+";
    private static final String PREVIOUS_PAGE = "←";
    private static final String NEXT_PAGE = "→";
    private static final String SEARCH_SYMBOL = "🔍";

    private static Component buildButton(
            Component label,
            NamedTextColor color,
            TextDecoration.State bold,
            TextDecoration.State italic,
            @Nullable ClickEvent clickEvent,
            @Nullable Component hoverText
    ) {
        Style.Builder style = Style.style()
                .color(color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, italic);
        if (clickEvent != null) {
            style.clickEvent(clickEvent);
        }
        if (hoverText != null) {
            style.hoverEvent(HoverEvent.showText(hoverText));
        }
        return text("[").append(label).append(text("]")).style(style.build());
    }

    private static Component buildSuggestButton(
            NamedTextColor color,
            String command,
            String symbol,
            Component hoverText
    ) {
        return buildButton(
                text(symbol),
                color,
                TextDecoration.State.TRUE,
                TextDecoration.State.NOT_SET,
                ClickEvent.suggestCommand(command),
                hoverText
        );
    }

    private static Component buildInactiveButton(String symbol) {
        return buildButton(
                text(symbol),
                NamedTextColor.DARK_GRAY,
                TextDecoration.State.NOT_SET,
                TextDecoration.State.NOT_SET,
                null,
                null
        );
    }

    public static Component getListSearchButton(ListTarget target, ListOptions options) {
        return buildButton(
                text(SEARCH_SYMBOL),
                NamedTextColor.AQUA,
                TextDecoration.State.FALSE,
                TextDecoration.State.FALSE,
                ClickEvent.suggestCommand(listSearchCmd(target, options)),
                translatable("button.list.search")
        );
    }

    public static Component getListViewToggleButton(ListTarget target, ListOptions options) {
        boolean nextGroupByLists = !options.groupByLists();
        String view = nextGroupByLists ? "tree" : "flat";
        return buildButton(
                translatable("waypoint.list.view." + view),
                NamedTextColor.AQUA,
                TextDecoration.State.FALSE,
                TextDecoration.State.FALSE,
                ClickEvent.runCommand(listViewCmd(target, options, nextGroupByLists)),
                translatable("button.list.view." + view)
        );
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
        NamedTextColor color;
        TextDecoration.State bold;
        ClickEvent clickEvent = null;
        Component hoverText = null;
        if (!enabled) {
            color = NamedTextColor.DARK_GRAY;
            bold = TextDecoration.State.FALSE;
            hoverText = translatable("button.sort.order.unavailable");
        } else if (selected) {
            color = NamedTextColor.GOLD;
            bold = TextDecoration.State.TRUE;
        } else {
            color = NamedTextColor.AQUA;
            bold = TextDecoration.State.FALSE;
            clickEvent = ClickEvent.runCommand(command);
            hoverText = translatable(hoverTranslationKey);
        }
        return buildButton(
                label,
                color,
                bold,
                TextDecoration.State.FALSE,
                clickEvent,
                hoverText
        );
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
                PREVIOUS_PAGE,
                        listPageCmd(target, options, options.pageNumber() - 1),
                        "button.page.previous"
                )
                : buildInactiveButton(PREVIOUS_PAGE);
        Component next = options.pageNumber() < totalPages
                ? pageButton(
                NEXT_PAGE,
                        listPageCmd(target, options, options.pageNumber() + 1),
                        "button.page.next"
                )
                : buildInactiveButton(NEXT_PAGE);
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
        return buildButton(
                text(symbol),
                NamedTextColor.AQUA,
                TextDecoration.State.NOT_SET,
                TextDecoration.State.NOT_SET,
                ClickEvent.runCommand(command),
                translatable(hoverTranslationKey)
        );
    }

    public static Component replaceButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildSuggestButton(
                NamedTextColor.AQUA,
                editCmd(dimensionName, listName, waypoint.name(), waypoint),
                REPLACE_SYMBOL,
                Component.translatable("button.replace")
        );
    }

    public static Component restoreButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildSuggestButton(
                NamedTextColor.LIGHT_PURPLE,
                addCmd(dimensionName, listName, waypoint),
                RESTORE_SYMBOL,
                Component.translatable("button.restore")
        );
    }

    public static Component removeButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildSuggestButton(
                NamedTextColor.RED,
                removeCmd(dimensionName, listName, waypoint),
                REMOVE_SYMBOL,
                Component.translatable("button.remove")
        );
    }

    public static Component editButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildSuggestButton(
                NamedTextColor.YELLOW,
                editCmd(dimensionName, listName, waypoint.name(), waypoint),
                EDIT_SYMBOL,
                Component.translatable("button.edit")
        );
    }

    public static Component addWaypointButton(String dimensionName, String listName, SimpleWaypoint waypoint) {
        return buildSuggestButton(
                NamedTextColor.GREEN,
                addCmd(dimensionName, listName, waypoint),
                ADD_SYMBOL,
                Component.translatable("button.add.waypoint")
        );
    }

    public static Component addListButton(String dimensionName, String listName) {
        return buildSuggestButton(
                NamedTextColor.GREEN,
                addListCmd(dimensionName, listName),
                ADD_SYMBOL,
                Component.translatable("button.add.list")
        );
    }
}
