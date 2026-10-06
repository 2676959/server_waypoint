package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/**
 * How remote screens show servers, lists and waypoints (spec 14). Labels from another server stay
 * literal text of at most 256 characters, so a remote name can never carry its own clicks.
 */
public final class RemoteRefs {
    static final int MAX_LABEL = 256;

    private RemoteRefs() {
    }

    /** A remote label as literal text; a blank one shows its identity in quotes. */
    public static Component label(String display, String identity) {
        return display.isBlank() ? text("\"" + truncate(identity) + "\"") : text(truncate(display));
    }

    /** The label, followed by " (identity)" for plain-text viewers when the two differ. */
    public static Component label(Viewer viewer, String display, String identity) {
        Component label = label(display, identity);
        return viewer.plainText() && !display.equals(identity)
                ? Chat.concat(label, text(" (" + truncate(identity) + ")"))
                : label;
    }

    static String truncate(String value) {
        return value.length() > MAX_LABEL ? value.substring(0, MAX_LABEL) + Chat.ELLIPSIS : value;
    }

    /** The server's name; plain-text viewers always read "Display (id)". */
    public static Component serverName(Viewer viewer, Server server) {
        Component name = label(server.displayName(), server.id().value());
        return viewer.plainText() ? Chat.concat(name, text(" (" + truncate(server.id().value()) + ")")) : name;
    }

    /** Shared server-status colors for command feedback and client details. */
    public static NamedTextColor stateColor(RemoteCatalogState state) {
        return switch (state) {
            case AVAILABLE -> GREEN;
            case STALE -> YELLOW;
            case UNAVAILABLE -> RED;
            case UNAUTHORIZED -> DARK_GRAY;
        };
    }

    /** The coloured ●: green available, yellow stale, red unreachable, dark gray no access. */
    public static Component dot(Viewer viewer, Server server) {
        NamedTextColor color = stateColor(server.state());
        Tooltip tooltip = Tooltip.of("wp.remote.state." + switch (server.state()) {
            case AVAILABLE -> "available";
            case STALE -> "stale";
            case UNAVAILABLE -> "unreachable";
            case UNAUTHORIZED -> "no_access";
        });
        if (server.state() == RemoteCatalogState.STALE) {
            tooltip = tooltip.line("wp.remote.state.stale.detail");
        }
        return Chat.hover(viewer, text(Chat.DOT, color), tooltip);
    }

    /** What follows a server's name: its waypoint count, or a status word; plain text adds "stale". */
    public static Component detail(Viewer viewer, Server server) {
        if (!server.readable()) {
            return translatable(server.state() == RemoteCatalogState.UNAUTHORIZED
                    ? "wp.remote.status.no_access" : "wp.remote.status.unreachable", GRAY);
        }
        if (server.waypointCount() == 0) {
            return translatable("wp.remote.status.empty", GRAY);
        }
        return Chat.join(text(String.valueOf(server.waypointCount()), GRAY),
                viewer.plainText() && server.state() == RemoteCatalogState.STALE
                        ? translatable("wp.remote.status.stale", GRAY) : null);
    }

    /** The server's name opening it: white with waypoints, gray when empty, and unclickable when unreadable. */
    public static Component serverLink(Viewer viewer, Server server) {
        Component name = serverName(viewer, server);
        if (!server.readable()) {
            return Chat.hover(viewer, Chat.colored(name, GRAY), serverTooltip(server, null));
        }
        return Chat.link(viewer, name, server.waypointCount() > 0 ? WHITE : GRAY,
                Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                serverTooltip(server, "wp.hint.open"));
    }

    /** The name, the identity, and what a readable server holds. */
    public static Tooltip serverTooltip(Server server, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(server.displayName(), server.id().value()))
                .line(text(truncate(server.id().value())));
        if (server.readable()) {
            tooltip = tooltip.line(translatable("wp.in", Chat.count("wp.count.waypoint", server.waypointCount()),
                    Chat.count("wp.count.dimension", server.dimensions().size())));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** [AB] Name without clicks, for a waypoint the player is switching to. */
    public static Component plain(Viewer viewer, SimpleWaypoint waypoint) {
        return Chat.concat(Chat.colored(WaypointRefs.marker(viewer, waypoint), TextColor.color(waypoint.rgb())), text(" "),
                Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), WHITE));
    }

    /** [AB] Name: the initials teleport and the white name opens the read-only details. */
    public static Component reference(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                      SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        return Chat.concat(initials(dims, server, dimension, list, waypoint), text(" "),
                Chat.link(viewer, label(viewer, waypoint.displayName(), waypoint.name()), WHITE,
                        Click.run(command("details", server, dimension, list, waypoint)),
                        waypointTooltip(dims, server, dimension, waypoint, "wp.hint.details")));
    }

    /** [AB] Name whose gold name only shows its tooltip, for the details title. */
    public static Component title(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                  SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        return Chat.concat(initials(dims, server, dimension, list, waypoint), text(" "),
                Chat.hover(viewer, Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), GOLD),
                        waypointTooltip(dims, server, dimension, waypoint, null)));
    }

    /**
     * [AB] teleports only on an available server and with the remote teleport permission. On a
     * stale server it says it is off; otherwise it shows the waypoint tooltip.
     */
    public static Component initials(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                     SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component initials = WaypointRefs.marker(viewer, waypoint);
        TextColor color = TextColor.color(waypoint.rgb());
        boolean permitted = viewer.can(Viewer.Permission.REMOTE_TP);
        if (permitted && server.available()) {
            return Chat.link(viewer, initials, color, Click.run(command("tp", server, dimension, list, waypoint)),
                    teleportTooltip(dims, server, dimension, waypoint));
        }
        Tooltip tooltip = permitted && server.state() == RemoteCatalogState.STALE
                ? Tooltip.of("wp.remote.teleport.stale", label(server.displayName(), server.id().value()))
                : waypointTooltip(dims, server, dimension, waypoint, null);
        return Chat.hover(viewer, Chat.colored(initials, color), tooltip);
    }

    /** [AB] Name, the list in Flat rows and search results, and the coordinates for plain-text viewers. */
    public static Component row(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                SimpleWaypoint waypoint, boolean withList) {
        Viewer viewer = dims.viewer();
        return Chat.join(reference(dims, server, dimension, list, waypoint),
                withList ? listLink(dims, server, dimension, list, GRAY, ListQuery.DEFAULT) : null,
                viewer.plainText() ? text(DimensionStyle.coordinates(waypoint.pos()), GRAY) : null);
    }

    /** Name, description, coordinates, paired coordinates, "On Survival in Overworld", and a hint. */
    public static Tooltip waypointTooltip(DimensionStyle dims, Server server, String dimension, SimpleWaypoint waypoint,
                                          @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(waypoint.displayName(), waypoint.name()));
        if (!waypoint.description().isBlank()) {
            tooltip = tooltip.line(text(truncate(waypoint.description())));
        }
        tooltip = tooltip.line(text(DimensionStyle.coordinates(waypoint.pos()), WHITE));
        Component paired = dims.pairedCoordinates(dimension, waypoint.pos());
        if (paired != null) {
            tooltip = tooltip.line(paired);
        }
        tooltip = tooltip.line(translatable("wp.remote.on", label(server.displayName(), server.id().value()),
                dims.name(dimension)));
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    public static Tooltip teleportTooltip(DimensionStyle dims, Server server, String dimension, SimpleWaypoint waypoint) {
        return Tooltip.of(translatable("wp.teleport.to", label(waypoint.displayName(), waypoint.name())))
                .line(text(DimensionStyle.coordinates(waypoint.pos()), WHITE))
                .line(translatable("wp.remote.teleport.switches", label(server.displayName(), server.id().value())));
    }

    /** A list name opening the list on its server. */
    public static Component listLink(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                     TextColor color, ListQuery query) {
        Viewer viewer = dims.viewer();
        return Chat.link(viewer, label(viewer, list.displayName(), list.name()), color,
                Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(query)),
                listTooltip(list, "wp.hint.open"));
    }

    /** Farms · 7 waypoints, then the identity when the display name differs. */
    public static Tooltip listTooltip(WaypointList list, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.join(label(list.displayName(), list.name()),
                Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY)));
        if (!list.displayName().equals(list.name())) {
            tooltip = tooltip.line(text(truncate(list.name())));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** /wp remote <action> <server> <dimension> <list> <waypoint>, string identities quoted as needed. */
    public static String command(String action, Server server, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return "/wp remote " + action + " " + escapeListName(server.id().value()) + " " + dimension
                + " " + escapeListName(list.name()) + " " + escapeListName(waypoint.name());
    }
}
