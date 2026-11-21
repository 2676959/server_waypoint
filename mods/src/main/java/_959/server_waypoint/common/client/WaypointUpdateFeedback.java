package _959.server_waypoint.common.client;

import _959.server_waypoint.common.server.command.CommandChatIcons;
import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.WaypointRefs;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Set;

import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Formats notifications from the applied data and the receiving client's permissions. */
final class WaypointUpdateFeedback {
    private WaypointUpdateFeedback() {
    }

    static Component chat(WaypointModificationMessage update, WaypointList list, SimpleWaypoint waypoint,
                          boolean canTeleport) {
        Viewer viewer = new Viewer(canTeleport ? Set.of(Viewer.Permission.TP) : Set.of(), true,
                false, null, null, 0F, CommandChatIcons.INSTANCE, update.scope());
        String dimension = update.dimensionName();
        // The update supplies the dimension ID. Use it to style the tooltip.
        DimensionStyle dims = DimensionStyle.local(viewer, Map.of(dimension, dimension));
        var waypointRef = update.type() == WaypointModificationType.REMOVE
                ? WaypointRefs.plain(viewer, waypoint)
                : WaypointRefs.reference(dims, dimension, list, waypoint);
        String key = "server_waypoint.update_notification.chat." + action(update.type());
        if (update.type() == WaypointModificationType.UPDATE) {
            return TextHelper.toMinecraft(translatable(key, GRAY, waypointRef));
        }
        var listRef = WaypointRefs.listLink(dims, list, WHITE,
                Click.run(ListTarget.list(dimension, list.name()).base()), "wp.hint.open");
        return TextHelper.toMinecraft(translatable(key, GRAY, waypointRef, listRef));
    }

    static Component toast(WaypointModificationType type, String displayName) {
        return Component.translatable("server_waypoint.update_notification." + action(type), displayName);
    }

    private static String action(WaypointModificationType type) {
        return switch (type) {
            case ADD -> "added";
            case UPDATE -> "updated";
            case REMOVE -> "removed";
            case ADD_LIST, REMOVE_LIST -> throw new IllegalArgumentException("Expected a waypoint modification");
        };
    }
}
