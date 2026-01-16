package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import static _959.server_waypoint.util.CommandGenerator.*;
import static net.kyori.adventure.text.Component.text;

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
