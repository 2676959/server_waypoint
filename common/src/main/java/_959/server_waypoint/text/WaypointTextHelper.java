package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.BlockPosConverter.netherToOverWorld;
import static _959.server_waypoint.util.BlockPosConverter.overWorldToNether;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class WaypointTextHelper {
    public static final Style DEFAULT_STYLE = Style.style().color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false).build();

    public static NamedTextColor colorByIndex(final int value) {
        return switch (value) {
            case  0 -> NamedTextColor.BLACK;
            case  1 -> NamedTextColor.DARK_BLUE;
            case  2 -> NamedTextColor.DARK_GREEN;
            case  3 -> NamedTextColor.DARK_AQUA;
            case  4 -> NamedTextColor.DARK_RED;
            case  5 -> NamedTextColor.DARK_PURPLE;
            case  6 -> NamedTextColor.GOLD;
            case  7 -> NamedTextColor.GRAY;
            case  8 -> NamedTextColor.DARK_GRAY;
            case  9 -> NamedTextColor.BLUE;
            case 10 -> NamedTextColor.GREEN;
            case 11 -> NamedTextColor.AQUA;
            case 12 -> NamedTextColor.RED;
            case 13 -> NamedTextColor.LIGHT_PURPLE;
            case 14 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }

    public static int colorToIndex(final NamedTextColor color) {
        return switch (NamedTextColor.NAMES.key(color)) {
            case "black" ->  0;
            case "dark_blue" ->  1;
            case "dark_green" ->  2;
            case "dark_aqua" ->  3;
            case "dark_red" ->  4;
            case "dark_purple" ->  5;
            case "gold" ->  6;
            case "gray" ->  7;
            case "dark_gray" ->  8;
            case "blue" ->  9;
            case "green" -> 10;
            case "aqua" -> 11;
            case "red" -> 12;
            case "light_purple" -> 13;
            case "yellow" -> 14;
            case null -> 0;
            default -> 15;
        };
    }

    public static Component defaultWaypointText(SimpleWaypoint waypoint, String dimensionName, String listName) {
        return waypointTextWithTp(waypoint, tpCmd(dimensionName, listName, waypoint.name()), waypointHoverText(waypoint, dimensionName));
    }

    public static Component basicWaypointText(SimpleWaypoint waypoint, @Nullable String command, Component commandInfo, Component waypointInfo) {
        Style initialsStyle;
        if (command == null) {
            initialsStyle = Style.style()
                    .decoration(TextDecoration.BOLD, true)
                    .color(colorByIndex(waypoint.colorIdx()))
                    .build();
        } else {
            initialsStyle = Style.style()
                    .decoration(TextDecoration.BOLD, true)
                    .color(colorByIndex(waypoint.colorIdx()))
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(commandInfo))
                    .build();
        }
        Component waypointText = Component.text(
                "[" + waypoint.initials() + "]"
        ).style(initialsStyle).append(Component.text(" ").style(DEFAULT_STYLE));
        Style nameStyle = Style.style()
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false)
                .hoverEvent(HoverEvent.showText(waypointInfo))
                .build();
        return waypointText.append(Component.text(waypoint.name()).style(nameStyle));
    }

    public static Component waypointTextWithTp(SimpleWaypoint waypoint, @NotNull String tpCommand, Component hoverText) {
        return basicWaypointText(waypoint, tpCommand, Component.translatable("button.initials.tp"), hoverText);
    }

    public static Component waypointHoverText(SimpleWaypoint waypoint, String dimensionName) {
        WaypointPos pos = waypoint.pos();
        Component hover = Component.text(pos.toShortString());
        if (MINECRAFT_OVERWORLD.equals(dimensionName)) {
            return hover.appendNewline().append(Component.text(overWorldToNether(pos).toShortString()).color(NamedTextColor.RED));
        } else if (MINECRAFT_THE_NETHER.equals(dimensionName)) {
            return hover.appendNewline().append(Component.text(netherToOverWorld(pos).toShortString()).color(NamedTextColor.GREEN));
        }
        return hover;
    }

    public static Component dimensionNameWithColor(String dimensionName) {
        return Component.text(dimensionName).color(getDimensionColor(dimensionName));
    }

    public static NamedTextColor getDimensionColor(String dimensionName) {
        return switch (dimensionName) {
            case MINECRAFT_OVERWORLD -> NamedTextColor.GREEN;
            case MINECRAFT_THE_NETHER -> NamedTextColor.RED;
            case MINECRAFT_THE_END ->  NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.YELLOW;
        };
    }
}
