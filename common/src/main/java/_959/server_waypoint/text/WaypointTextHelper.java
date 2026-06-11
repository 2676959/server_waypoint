package _959.server_waypoint.text;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static _959.server_waypoint.text.TextButton.editButton;
import static _959.server_waypoint.text.TextButton.removeButton;
import static _959.server_waypoint.util.BlockPosConverter.netherToOverWorld;
import static _959.server_waypoint.util.BlockPosConverter.overWorldToNether;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.util.VanillaDimensionNames.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class WaypointTextHelper {
    public static final Style DEFAULT_STYLE = Style.style().color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false).build();

    public static Component waypointTextWithTp(SimpleWaypoint waypoint, String dimensionName, String listName) {
        return basicWaypointText(waypoint, tpCmd(dimensionName, listName, waypoint.name()), Component.translatable("button.initials.tp"), waypointHoverText(waypoint, dimensionName));
    }

    public static Component waypointTextNoTp(SimpleWaypoint waypoint, String dimensionName) {
        return basicWaypointText(waypoint, null, null, waypointHoverText(waypoint, dimensionName));
    }

    public static Component basicWaypointText(SimpleWaypoint waypoint, @Nullable String command, Component commandInfo, Component waypointInfo) {
        Style initialsStyle;
        if (command == null) {
            initialsStyle = Style.style()
                    .decoration(TextDecoration.BOLD, true)
                    .color(TextColor.color(waypoint.rgb()))
                    .build();
        } else {
            initialsStyle = Style.style()
                    .decoration(TextDecoration.BOLD, true)
                    .color(TextColor.color(waypoint.rgb()))
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(commandInfo))
                    .build();
        }
        Component waypointText = text(
                "[" + waypoint.initials() + "]"
        ).style(initialsStyle).append(text(" ").style(DEFAULT_STYLE));
        Style nameStyle = Style.style()
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false)
                .hoverEvent(HoverEvent.showText(waypointInfo))
                .build();
        return waypointText.append(text(waypoint.name()).style(nameStyle));
    }

    public static Component waypointHoverText(SimpleWaypoint waypoint, String dimensionName) {
        WaypointPos pos = waypoint.pos();
        Component hover = text(pos.toShortString());
        if (MINECRAFT_OVERWORLD.equals(dimensionName)) {
            return hover.appendNewline().append(text(overWorldToNether(pos).toShortString()).color(NamedTextColor.RED));
        } else if (MINECRAFT_THE_NETHER.equals(dimensionName)) {
            return hover.appendNewline().append(text(netherToOverWorld(pos).toShortString()).color(NamedTextColor.GREEN));
        }
        return hover;
    }

    public static Component dimensionNameWithColor(String dimensionName) {
        return text(dimensionName).color(getDimensionColor(dimensionName));
    }

    public static NamedTextColor getDimensionColor(String dimensionName) {
        return switch (dimensionName) {
            case MINECRAFT_OVERWORLD -> NamedTextColor.GREEN;
            case MINECRAFT_THE_NETHER -> NamedTextColor.RED;
            case MINECRAFT_THE_END ->  NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.YELLOW;
        };
    }
    
    public static Component getDimensionListText(WaypointFileManager fileManager, boolean isPart, boolean withEdit, boolean withRemove, boolean withTp) {
        String dimensionName = fileManager.getDimensionName();
        Component dimensionListText = isPart ?
                dimensionNameWithColor(dimensionName).appendNewline() :
                text("").appendNewline().append(dimensionNameWithColor(dimensionName)).appendNewline();
        Map<String, WaypointList> lists = fileManager.getWaypointListMap();
        for (WaypointList waypointList : lists.values()) {
            dimensionListText = dimensionListText.append(getWaypointListText(waypointList, dimensionName, 1, true, withEdit, withRemove, withTp));
        }
        return dimensionListText;
    }
    
    public static Component getWaypointListText(WaypointList waypointList, String dimensionName, int indentLevel, boolean isPart, boolean withEdit, boolean withRemove, boolean withTp) {
        String listName = waypointList.name();
        Component listText = isPart ?
                text("  ".repeat(indentLevel) + listName, NamedTextColor.WHITE) :
                text("")
                        .appendNewline()
                        .append(text("  ".repeat(indentLevel) + listName, NamedTextColor.WHITE))
                        .appendSpace().append(text("⬅")).appendSpace().append(dimensionNameWithColor(dimensionName));
        listText = listText.decoration(TextDecoration.BOLD, true);
        listText = listText.appendNewline();
        int secondLevel = indentLevel + 1;
        if (waypointList.isEmpty()) {
            listText = listText.append(text("  ".repeat(secondLevel)))
                    .append(translatable("waypoint.empty.list.placeholder", NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, true).appendNewline());
            return listText;
        }
        for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
            Component waypointText = text("  ".repeat(secondLevel)).decoration(TextDecoration.BOLD, false);
            if (withEdit) {
                waypointText = waypointText.append(editButton(dimensionName, listName, waypoint)).appendSpace();
            }
            if (withRemove) {
                waypointText = waypointText.append(removeButton(dimensionName, listName, waypoint)).appendSpace();
            }
            if (withTp) {
                waypointText = waypointText.append(waypointTextWithTp(waypoint, dimensionName, listName));
            } else {
                waypointText = waypointText.append(waypointTextNoTp(waypoint, dimensionName));
            }
            listText = listText.append(waypointText).appendNewline();
        }
        return listText;
    }
}
