package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;

import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** What other players read when someone changes waypoints (spec 13): a white name, gray verbs, clickable references. */
public final class Broadcasts {
    private Broadcasts() {
    }

    /** Steve added [PP] Pumpkin Patch to Farms */
    public static Component added(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                  SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.added", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve updated [MH] Main Home */
    public static Component updated(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.updated", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint));
    }

    /** Steve removed [MH] Main Home from Home Bases */
    public static Component removed(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.removed", GRAY, player(actor), WaypointRefs.plain(dims.viewer(), waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve restored [MH] Main Home to Home Bases */
    public static Component restored(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                     SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.restored", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve created the list Farms in Overworld */
    public static Component createdList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.created_list", GRAY, player(actor), Results.listLink(dims, dimension, list),
                dims.name(dimension));
    }

    /** Steve updated the list Farms in Overworld */
    public static Component updatedList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.updated_list", GRAY, player(actor), Results.listLink(dims, dimension, list),
                dims.name(dimension));
    }

    /** Steve removed the list Storage from Nether */
    public static Component removedList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.removed_list", GRAY, player(actor),
                Chat.colored(WaypointRefs.label(dims.viewer(), list.displayName(), list.name()), WHITE), dims.name(dimension));
    }

    private static Component player(Component actor) {
        return Chat.colored(actor, WHITE);
    }
}
