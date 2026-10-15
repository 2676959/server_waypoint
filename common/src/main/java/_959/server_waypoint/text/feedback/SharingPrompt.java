package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.addCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

/** The prompt a player gets after sharing a Xaero's Minimap waypoint in chat (spec 13). */
public final class SharingPrompt {
    private SharingPrompt() {
    }

    /** Shared waypoint [AB] Name in Overworld, then the lists to save it to. */
    public static Component found(DimensionStyle dims, String dimension, SimpleWaypoint waypoint, List<WaypointList> lists) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines().add(translatable("wp.sharing.found", GRAY,
                WaypointRefs.plain(viewer, waypoint), dims.name(dimension)));
        if (lists.isEmpty()) {
            Component newList = ListActions.newList(dims, dimension);
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).build();
        }
        List<Component> links = lists.stream().map(list -> Chat.link(viewer,
                WaypointRefs.label(viewer, list.displayName(), list.name()), GREEN,
                Click.suggest(addCmd(dimension, list.name(), waypoint)),
                Tooltip.of("wp.sharing.save_to.tooltip", WaypointRefs.label(list.displayName(), list.name()))
                        .hint("wp.hint.confirm"))).toList();
        return lines.line(translatable("wp.sharing.save_to", GRAY), text("  "), Chat.join(links)).build();
    }

    /** ✘ This server has no dimension x. The shared waypoint is in the line the player just sent. */
    public static Component unknownDimension(DimensionStyle dims, String dimension, SimpleWaypoint waypoint) {
        return Chat.error(translatable("wp.error.sharing.dimension", text(dimension)));
    }
}
