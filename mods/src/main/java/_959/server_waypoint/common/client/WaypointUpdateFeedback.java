package _959.server_waypoint.common.client;

import _959.server_waypoint.common.server.command.CommandChatIcons;
import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.WaypointRefs;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static net.kyori.adventure.text.Component.translatable;
import static _959.server_waypoint.text.chat.Chat.colored;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Formats notifications from the applied data and the receiving client's permissions. */
final class WaypointUpdateFeedback {
    private WaypointUpdateFeedback() {
    }

    enum Action {
        ADD("added"),
        UPDATE("updated"),
        REMOVE("removed"),
        RESTORE("restored"),
        ADD_LIST("created_list"),
        UPDATE_LIST("updated_list"),
        REMOVE_LIST("removed_list");

        private final String translation;

        Action(String translation) {
            this.translation = translation;
        }
    }

    record Update(WaypointScope scope, String dimensionName, Action action, WaypointList list,
                  @Nullable SimpleWaypoint waypoint) {
        static Update fromDelta(WaypointModificationMessage delta, WaypointList list, @Nullable SimpleWaypoint waypoint) {
            Action action = switch (delta.type()) {
                case ADD -> Action.ADD;
                case UPDATE -> Action.UPDATE;
                case REMOVE -> Action.REMOVE;
                case RESTORE -> Action.RESTORE;
                case ADD_LIST -> Action.ADD_LIST;
                case REMOVE_LIST -> Action.REMOVE_LIST;
            };
            return new Update(delta.scope(), delta.dimensionName(), action, list, waypoint);
        }

        String displayName() {
            return waypoint == null ? list.displayName() : waypoint.displayName();
        }
    }

    static Component chat(Update update, boolean canTeleport) {
        Viewer viewer = new Viewer(canTeleport ? Set.of(Viewer.Permission.TP) : Set.of(), true,
                false, null, null, 0F, CommandChatIcons.INSTANCE, update.scope());
        String dimension = update.dimensionName();
        // The update supplies the dimension ID. Use it to style the tooltip.
        DimensionStyle dims = DimensionStyle.local(viewer, Map.of(dimension, dimension));
        String key = "server_waypoint.update_notification.chat." + update.action().translation;
        WaypointList list = update.list();
        SimpleWaypoint waypoint = update.waypoint();
        if (waypoint == null) {
            var listRef = update.action() == Action.REMOVE_LIST
                    ? colored(WaypointRefs.label(viewer, list.displayName(), list.name()), WHITE)
                    : listLink(dims, dimension, list);
            return TextHelper.toMinecraft(translatable(key, GRAY, listRef));
        }
        var waypointRef = update.action() == Action.REMOVE
                ? WaypointRefs.plain(viewer, waypoint)
                : WaypointRefs.reference(dims, dimension, list, waypoint);
        if (update.action() == Action.UPDATE) {
            return TextHelper.toMinecraft(translatable(key, GRAY, waypointRef));
        }
        return TextHelper.toMinecraft(translatable(key, GRAY, waypointRef, listLink(dims, dimension, list)));
    }

    private static net.kyori.adventure.text.Component listLink(DimensionStyle dims, String dimension, WaypointList list) {
        return WaypointRefs.listLink(dims, list, WHITE, Click.run(ListTarget.list(dimension, list.name()).base()), "wp.hint.open");
    }

    static Component toast(Update update) {
        return Component.translatable("server_waypoint.update_notification." + update.action().translation, update.displayName());
    }
}
