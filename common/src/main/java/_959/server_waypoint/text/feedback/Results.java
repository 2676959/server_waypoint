package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.addListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeCmd;
import static _959.server_waypoint.util.StringCommandBuilder.restoreCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Result lines (spec 13): a green ✔, what happened, then the actions that follow from it. */
public final class Results {
    private Results() {
    }

    /** ✔ Added [PP] Pumpkin Patch to Farms   Details · Navigate · Undo */
    public static Component added(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component name = WaypointRefs.label(waypoint.displayName(), waypoint.name());
        return Chat.ok(translatable("wp.result.added", WaypointRefs.reference(dims, dimension, list, waypoint),
                        listLink(dims, dimension, list)),
                Arrays.asList(
                        Chat.control(viewer, translatable("wp.action.details"), AQUA,
                                Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())),
                                WaypointRefs.waypointTooltip(dims, dimension, waypoint, "wp.hint.details")),
                        viewer.can(Viewer.Permission.NAVIGATE)
                                ? Chat.control(viewer, translatable("wp.action.navigate"), LIGHT_PURPLE,
                                Click.run(navigateCmd(dimension, list.name(), waypoint.name())),
                                Tooltip.of("wp.action.navigate.tooltip", name))
                                : null,
                        viewer.can(Viewer.Permission.REMOVE)
                                ? Chat.control(viewer, translatable("wp.action.undo"), RED,
                                Click.suggest(removeCmd(dimension, list.name(), waypoint)),
                                Tooltip.of("wp.action.undo.added", name).hint("wp.hint.confirm"))
                                : null));
    }

    /** ✔ Created the list Farms in Overworld   Add here · Open */
    public static Component createdList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        String open = ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT);
        return Chat.ok(translatable("wp.result.created_list", listLink(dims, dimension, list), dims.name(dimension)),
                Arrays.asList(
                        ListActions.add(dims, dimension, list),
                        Chat.control(viewer, translatable("wp.action.open"), AQUA, Click.run(open),
                                Tooltip.of("wp.open", WaypointRefs.label(list.displayName(), list.name())))));
    }

    /** ✔ Removed [MH] Main Home from Home Bases   Restore */
    public static Component removed(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                    String token) {
        Viewer viewer = dims.viewer();
        Component gone = WaypointRefs.plain(viewer, waypoint);
        String restore = restoreCmd(token);
        if (viewer.plainText()) {
            return Chat.ok(translatable("wp.result.removed.plain", gone, listLink(dims, dimension, list), text(restore)));
        }
        return Chat.ok(translatable("wp.result.removed", gone, listLink(dims, dimension, list)), List.of(
                Chat.link(viewer, translatable("wp.action.restore"), GREEN, Click.run(restore),
                        Tooltip.of("wp.action.restore.tooltip", WaypointRefs.label(waypoint.displayName(), waypoint.name()),
                                WaypointRefs.label(list.displayName(), list.name())))));
    }

    /** ✔ Removed the list Storage from Nether   Undo */
    public static Component removedList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        Component name = WaypointRefs.label(viewer, list.displayName(), list.name());
        return Chat.ok(translatable("wp.result.removed_list", Chat.colored(name, WHITE), dims.name(dimension)),
                Arrays.asList(viewer.can(Viewer.Permission.ADD)
                        ? Chat.control(viewer, translatable("wp.action.undo"), GREEN,
                        Click.run(addListCmd(dimension, list.name())),
                        Tooltip.of("wp.action.undo.removed_list", WaypointRefs.label(list.displayName(), list.name())))
                        : null));
    }

    /** ✔ Restored [MH] Main Home to Home Bases */
    public static Component restored(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.ok(translatable("wp.result.restored", WaypointRefs.reference(dims, dimension, list, waypoint),
                listLink(dims, dimension, list)));
    }

    /** ✔ Teleported to [MH] Main Home */
    public static Component teleported(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.ok(translatable("wp.result.teleported", WaypointRefs.reference(dims, dimension, list, waypoint)));
    }

    /** ✔ Reloaded the configuration and language files, then the external languages that were loaded. */
    public static Component reloaded(List<String> languages) {
        return new ChatLines()
                .add(Chat.ok(translatable("wp.result.reloaded")))
                .add(languages.isEmpty() ? null
                        : translatable("wp.result.languages", GRAY, text(String.join(", ", languages))))
                .build();
    }

    /** ✔ Sent 12 waypoints to your map mod */
    public static Component sent(int waypoints) {
        return Chat.ok(translatable("wp.download.sent", Chat.count("wp.count.waypoint", waypoints)));
    }

    public static Component keyGenerated(String publicKey) {
        return Chat.ok(translatable("wp.result.key_generated", text(publicKey)));
    }

    /** The list name in white, opening the list. */
    static Component listLink(DimensionStyle dims, String dimension, WaypointList list) {
        return WaypointRefs.listLink(dims, list, WHITE,
                Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open");
    }
}
