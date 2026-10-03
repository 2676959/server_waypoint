package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.text.FormattedTextHelper;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;

/** Chat output that the map mod integrations show in this client's own chat. */
public final class MapModChatHelper {
    private MapModChatHelper() {
    }

    /**
     * [AB] Name for this client's own chat: the initials teleport, with the client's translation
     * of the hint, and the name shows the description and coordinates.
     */
    public static net.kyori.adventure.text.Component waypointText(SimpleWaypoint waypoint, String dimensionName, String listName) {
        net.kyori.adventure.text.Component where = net.kyori.adventure.text.Component.text(DimensionStyle.coordinates(waypoint.pos()));
        if (!waypoint.description().isEmpty()) {
            where = FormattedTextHelper.parse(waypoint.description()).appendNewline().append(where);
        }
        return net.kyori.adventure.text.Component.empty()
                .append(net.kyori.adventure.text.Component.text("[" + waypoint.initials() + "]", TextColor.color(waypoint.rgb()))
                        .clickEvent(ClickEvent.runCommand(tpCmd(dimensionName, listName, waypoint.name())))
                        .hoverEvent(HoverEvent.showText(net.kyori.adventure.text.Component.translatable("button.initials.tp"))))
                .append(net.kyori.adventure.text.Component.space())
                .append(net.kyori.adventure.text.Component.empty().color(NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(where))
                        .append(FormattedTextHelper.parse(waypoint.displayName())));
    }

    public static void displayClientMessage(Player player, Component message) {
        if (player == null) {
            return;
        }
        //? if >=26
        player.sendSystemMessage(message);
        //? if <26
        /*player.displayClientMessage(message, false);*/
    }
}
