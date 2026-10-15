package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.StringCommandBuilder.escapeArgument;
import static _959.server_waypoint.util.StringCommandBuilder.removeListCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

/** Actions on lists that several screens offer (spec 6.5, 6.7, 8.2). */
public final class ListActions {
    private ListActions() {
    }

    /** Adding to a list: where the viewer stands in their own dimension, by coordinates elsewhere. */
    public static Click addClick(Viewer viewer, String dimension, WaypointList list) {
        return viewer.isIn(dimension)
                ? Click.suggest("/wp add ~ ~ ~ " + escapeArgument(list.name()) + " ")
                : Click.suggest("/wp add " + dimension + " " + escapeArgument(list.name()) + " ");
    }

    public static Tooltip addTooltip(DimensionStyle dims, String dimension, WaypointList list) {
        Component name = WaypointRefs.label(list.displayName(), list.name());
        return dims.viewer().isIn(dimension)
                ? Tooltip.of("wp.add.here.tooltip", name).hint("wp.hint.type_name")
                : Tooltip.of("wp.add.elsewhere.tooltip", name, dims.name(dimension)).hint("wp.hint.type_coordinates");
    }

    /** Add here in the viewer's dimension, Add waypoint elsewhere; null without the add permission. */
    public static @Nullable Component add(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD)) {
            return null;
        }
        return Chat.control(viewer, translatable(viewer.isIn(dimension) ? "wp.add.here" : "wp.add.waypoint"), GREEN,
                addClick(viewer, dimension, list), addTooltip(dims, dimension, list));
    }

    /** The green + after a list name, two spaces after it. */
    public static @Nullable Component plus(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD) || viewer.plainText()) {
            return null;
        }
        return Chat.concat(text("  "), Chat.control(viewer, text("+"), GREEN, addClick(viewer, dimension, list),
                addTooltip(dims, dimension, list)));
    }

    public static @Nullable Component newList(DimensionStyle dims, String dimension) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD)) {
            return null;
        }
        return Chat.control(viewer, translatable("wp.new_list"), GREEN, Click.suggest("/wp add " + dimension + " "),
                Tooltip.of("wp.new_list.tooltip", dims.name(dimension)).hint("wp.hint.type_name"));
    }

    public static @Nullable Component removeList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.REMOVE)) {
            return null;
        }
        return Chat.control(viewer, translatable("wp.list.remove"), RED,
                Click.suggest(removeListCmd(dimension, list.name(), true)),
                Tooltip.of("wp.list.remove.tooltip").hint("wp.hint.confirm"));
    }
}
