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

import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.TextButtonBuilder.editButton;
import static _959.server_waypoint.text.TextButtonBuilder.removeButton;
import static _959.server_waypoint.util.BlockPosConverter.netherToOverWorld;
import static _959.server_waypoint.util.BlockPosConverter.overWorldToNether;
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
import static _959.server_waypoint.util.VanillaDimensionNames.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static _959.server_waypoint.text.FormattedTextHelper.parse;

public class WaypointTextHelper {
    public static final Style DEFAULT_STYLE = Style.style().color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false).build();

    public static Component waypointTextWithTp(SimpleWaypoint waypoint, String dimensionName, String listName) {
        waypoint = new SimpleWaypoint(waypoint);
        return basicWaypointText(waypoint, tpCmd(dimensionName, listName, waypoint.name()), Component.translatable("button.initials.tp"), waypointHoverText(waypoint, dimensionName));
    }

    public static Component waypointTextNoTp(SimpleWaypoint waypoint, String dimensionName) {
        waypoint = new SimpleWaypoint(waypoint);
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
        return waypointText.append(text("").style(nameStyle).append(parse(waypoint.displayName())));
    }

    public static Component waypointHoverText(SimpleWaypoint waypoint, String dimensionName) {
        WaypointPos pos = waypoint.pos();
        Component hover = text("");
        if (!waypoint.description().isEmpty()) {
            hover = hover.append(parse(waypoint.description())).appendNewline();
        }
        hover = hover.append(text(pos.toShortString()));
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
        return getWaypointListText(
                waypointList,
                waypointList.simpleWaypoints(),
                dimensionName,
                indentLevel,
                isPart,
                withEdit,
                withRemove,
                withTp
        );
    }

    public static Component getWaypointListText(
            WaypointList waypointList,
            List<SimpleWaypoint> waypoints,
            String dimensionName,
            int indentLevel,
            boolean isPart,
            boolean withEdit,
            boolean withRemove,
            boolean withTp
    ) {
        return getWaypointListText(
                waypointList,
                waypoints,
                dimensionName,
                indentLevel,
                isPart,
                withEdit,
                withRemove,
                withTp,
                null
        );
    }

    public static Component getWaypointListText(
            WaypointList waypointList,
            List<SimpleWaypoint> waypoints,
            String dimensionName,
            int indentLevel,
            boolean isPart,
            boolean withEdit,
            boolean withRemove,
            boolean withTp,
            @Nullable String listCommand
    ) {
        String listName = waypointList.name();
        Component listTitle = text("  ".repeat(indentLevel)).append(parse(waypointList.displayName()).colorIfAbsent(NamedTextColor.WHITE));
        if (listCommand != null) {
            listTitle = listTitle
                    .clickEvent(ClickEvent.runCommand(listCommand))
                    .hoverEvent(HoverEvent.showText(translatable(
                            "button.list.waypoint_list",
                            parse(waypointList.displayName())
                    )));
        }
        Component listHeader = text("").append(listTitle);
        if (!isPart) {
            listHeader = listHeader.appendSpace().append(text("⬅")).appendSpace()
                    .append(dimensionNameWithColor(dimensionName));
        }
        listHeader = listHeader.decoration(TextDecoration.BOLD, true);

        Component listText = text("");
        if (!isPart) {
            listText = listText.appendNewline();
        }
        listText = listText.append(listHeader).appendNewline();
        int secondLevel = indentLevel + 1;
        if (waypoints.isEmpty()) {
            listText = listText.append(text("  ".repeat(secondLevel)))
                    .append(translatable("waypoint.empty.list.placeholder", NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, true).appendNewline());
            return listText;
        }
        for (SimpleWaypoint waypoint : waypoints) {
            listText = listText.append(getWaypointText(
                    waypoint,
                    dimensionName,
                    listName,
                    secondLevel,
                    withEdit,
                    withRemove,
                    withTp
            )).appendNewline();
        }
        return listText;
    }

    public static Component getWaypointText(
            SimpleWaypoint waypoint,
            String dimensionName,
            String listName,
            int indentLevel,
            boolean withEdit,
            boolean withRemove,
            boolean withTp
    ) {
        waypoint = new SimpleWaypoint(waypoint);
        Component waypointText = text("  ".repeat(indentLevel)).decoration(TextDecoration.BOLD, false);
        if (withEdit) {
            waypointText = waypointText.append(editButton(dimensionName, listName, waypoint)).appendSpace();
        }
        if (withRemove) {
            waypointText = waypointText.append(removeButton(dimensionName, listName, waypoint)).appendSpace();
        }
        if (withTp) {
            return waypointText.append(waypointTextWithTp(waypoint, dimensionName, listName));
        }
        return waypointText.append(waypointTextNoTp(waypoint, dimensionName));
    }
}
