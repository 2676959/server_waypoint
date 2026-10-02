package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.text.FormattedTextHelper.parse;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/** How local screens refer to waypoints and lists (spec 2.3, 3). */
public final class WaypointRefs {
    private WaypointRefs() {
    }

    /** A display name as players read it; a blank one shows the identifier in quotes. */
    public static Component label(String display, String identifier) {
        return plainText(display).isBlank() ? text("\"" + identifier + "\"") : parse(display);
    }

    /** The label, followed by " (identifier)" for plain-text viewers when the two differ. */
    public static Component label(Viewer viewer, String display, String identifier) {
        Component label = label(display, identifier);
        if (viewer.plainText() && !plainText(display).equals(identifier)) {
            return Chat.concat(label, text(" (" + identifier + ")"));
        }
        return label;
    }

    /** [AB] Name: the initials teleport and the white name opens details. */
    public static Component reference(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.concat(initials(dims, dimension, list, waypoint), text(" "),
                name(dims, dimension, list, waypoint, NamedTextColor.WHITE, true));
    }

    /** [AB] Name whose name shows its tooltip without a click, for titles and pickers. */
    public static Component title(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                  TextColor nameColor) {
        return Chat.concat(initials(dims, dimension, list, waypoint), text(" "),
                name(dims, dimension, list, waypoint, nameColor, false));
    }

    /** [AB] Name without clicks, for a waypoint that is gone or not on this server yet. */
    public static Component plain(Viewer viewer, SimpleWaypoint waypoint) {
        return Chat.concat(Chat.colored(text("[" + waypoint.initials() + "]"), TextColor.color(waypoint.rgb())), text(" "),
                Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), NamedTextColor.WHITE));
    }

    /** [AB] in the waypoint colour: a teleport link with permission, otherwise coloured text with the waypoint tooltip. */
    public static Component initials(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component initials = text("[" + waypoint.initials() + "]");
        TextColor color = TextColor.color(waypoint.rgb());
        if (viewer.can(Viewer.Permission.TP)) {
            return Chat.link(viewer, initials, color, Click.run(tpCmd(dimension, list.name(), waypoint.name())),
                    teleportTooltip(dims, dimension, waypoint));
        }
        return Chat.hover(viewer, Chat.colored(initials, color), waypointTooltip(dims, dimension, waypoint, null));
    }

    private static Component name(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                  TextColor color, boolean details) {
        Viewer viewer = dims.viewer();
        Component label = label(viewer, waypoint.displayName(), waypoint.name());
        if (!details) {
            return Chat.hover(viewer, Chat.colored(label, color), waypointTooltip(dims, dimension, waypoint, null));
        }
        return Chat.link(viewer, label, color, Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())),
                waypointTooltip(dims, dimension, waypoint, "wp.hint.details"));
    }

    /** Name, description, coordinates, paired coordinates, distance or dimension, and a hint. */
    public static Tooltip waypointTooltip(DimensionStyle dims, String dimension, SimpleWaypoint waypoint,
                                          @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(waypoint.displayName(), waypoint.name()));
        if (!plainText(waypoint.description()).isBlank()) {
            tooltip = tooltip.line(parse(waypoint.description()));
        }
        tooltip = tooltip.line(text(DimensionStyle.coordinates(waypoint.pos()), NamedTextColor.WHITE));
        Component paired = dims.pairedCoordinates(dimension, waypoint.pos());
        if (paired != null) {
            tooltip = tooltip.line(paired);
        }
        tooltip = tooltip.line(where(dims, dimension, waypoint.pos()));
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    public static Tooltip teleportTooltip(DimensionStyle dims, String dimension, SimpleWaypoint waypoint) {
        return Tooltip.of(translatable("wp.teleport.to", label(waypoint.displayName(), waypoint.name())))
                .line(text(DimensionStyle.coordinates(waypoint.pos()), NamedTextColor.WHITE))
                .line(where(dims, dimension, waypoint.pos()));
    }

    /** "25 m away" in the viewer's dimension, otherwise "In Nether" with the dimension in its colour. */
    public static Component where(DimensionStyle dims, String dimension, WaypointPos pos) {
        Component distance = distance(dims.viewer(), dimension, pos);
        return distance != null
                ? translatable("wp.distance.away", distance)
                : translatable("wp.where.in", dims.name(dimension));
    }

    /** "25 m" or "1.5 km" from the viewer, or null outside the viewer's dimension. */
    public static @Nullable Component distance(Viewer viewer, String dimension, WaypointPos pos) {
        WaypointPos origin = viewer.position();
        if (origin == null || !viewer.isIn(dimension)) {
            return null;
        }
        double dx = pos.x() - origin.x();
        double dy = pos.y() - origin.y();
        double dz = pos.z() - origin.z();
        long metres = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (metres < 1000) {
            return translatable("wp.distance.metres", text(metres));
        }
        return translatable("wp.distance.kilometres", text(String.format(Locale.ROOT, "%.1f", metres / 1000.0)));
    }

    /** What follows a row's name: the gray distance for players, the coordinates in plain text. */
    public static @Nullable Component rowDetail(Viewer viewer, String dimension, WaypointPos pos) {
        if (viewer.plainText()) {
            return text(DimensionStyle.coordinates(pos), NamedTextColor.GRAY);
        }
        Component distance = distance(viewer, dimension, pos);
        return distance == null ? null : Chat.colored(distance, NamedTextColor.GRAY);
    }

    /** Farms · 7 waypoints, then the identifier when the display name differs, then the hint. */
    public static Tooltip listTooltip(WaypointList list, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.join(label(list.displayName(), list.name()),
                Chat.colored(Chat.count("wp.count.waypoint", list.size()), NamedTextColor.GRAY)));
        if (!plainText(list.displayName()).equals(list.name())) {
            tooltip = tooltip.line(text(list.name()));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** A list name that opens something; plain-text viewers read "Display (identifier)" when they differ. */
    public static Component listLink(DimensionStyle dims, WaypointList list, TextColor color, Click click,
                                     @Nullable String hint) {
        Viewer viewer = dims.viewer();
        return Chat.link(viewer, label(viewer, list.displayName(), list.name()), color, click, listTooltip(list, hint));
    }
}
