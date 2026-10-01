package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

/** The /wp menu (spec 5). Groups the viewer has nothing in are left out. */
public final class MenuScreen {
    private MenuScreen() {
    }

    public static Component menu(Viewer viewer, DimensionStyle dims, @Nullable PlacedWaypoint navigation,
                                 boolean remoteAvailable) {
        String here = Objects.requireNonNullElse(viewer.dimension(), "minecraft:overworld");
        ChatLines lines = new ChatLines();
        List<Component> extras = new ArrayList<>();
        if (viewer.hasMod()) {
            extras.add(Chat.link(viewer, translatable("wp.menu.open_gui"), AQUA, Click.run("/wp_gui"),
                    Tooltip.of("wp.menu.open_gui.tooltip").line("wp.menu.open_gui.detail")));
        }
        extras.add(Chat.link(viewer, translatable("wp.menu.help"), GRAY, Click.run("/wp help"),
                Tooltip.of("wp.menu.help.tooltip").line("wp.menu.help.detail")));
        if (viewer.can(Viewer.Permission.RELOAD)) {
            extras.add(Chat.link(viewer, translatable("wp.menu.reload"), GRAY, Click.suggest("/wp reload"),
                    Tooltip.of("wp.menu.reload.tooltip").hint("wp.hint.confirm")));
        }
        lines.line(translatable("wp.menu.title", GOLD), text("   "), Chat.join(extras));

        group(lines, "wp.menu.browse", Chat.join(
                Chat.link(viewer, translatable("wp.menu.this_dimension"), AQUA, Click.run("/wp list"),
                        Tooltip.of(translatable("wp.menu.this_dimension.tooltip", dims.name(here)))),
                Chat.link(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"), Tooltip.of("wp.all.tooltip")),
                remoteAvailable ? Chat.link(viewer, translatable("wp.menu.remote"), AQUA, Click.run("/wp remote"),
                        Tooltip.of("wp.menu.remote.tooltip")) : null,
                Chat.link(viewer, translatable("wp.search"), AQUA, Click.suggest("/wp list all search "),
                        Tooltip.of("wp.search.everywhere").hint("wp.hint.type_search"))));

        if (viewer.can(Viewer.Permission.ADD)) {
            group(lines, "wp.menu.create", Chat.join(
                    Chat.link(viewer, translatable("wp.menu.waypoint_here"), GREEN, Click.run("/wp add"),
                            Tooltip.of("wp.menu.waypoint_here.tooltip").line("wp.menu.waypoint_here.detail")),
                    Chat.link(viewer, translatable("wp.menu.list"), GREEN, Click.suggest("/wp add " + here + " "),
                            Tooltip.of(translatable("wp.new_list.tooltip", dims.name(here))).hint("wp.hint.type_name"))));
        }

        if (viewer.can(Viewer.Permission.NAVIGATE)) {
            Component navigationLink = Chat.link(viewer, translatable("wp.menu.navigation"), LIGHT_PURPLE,
                    Click.run("/wp navigate"), Tooltip.of("wp.menu.navigation.tooltip"));
            group(lines, "wp.menu.travel", navigation == null ? navigationLink : Chat.join(
                    translatable("wp.menu.navigation_to", GRAY, navigationLink, navigation.reference(dims)),
                    Chat.link(viewer, translatable("wp.navigation.stop"), RED, Click.run("/wp navigate disable"),
                            Tooltip.of("wp.navigation.stop.tooltip"))));
        }

        List<Component> transfer = new ArrayList<>();
        if (viewer.hasMod()) {
            transfer.add(Chat.link(viewer, translatable("wp.menu.download"), AQUA, Click.run("/wp download"),
                    Tooltip.of("wp.menu.download.tooltip")));
            if (viewer.can(Viewer.Permission.UPLOAD)) {
                transfer.add(Chat.link(viewer, translatable("wp.menu.upload"), AQUA, Click.run("/wp upload"),
                        Tooltip.of("wp.menu.upload.tooltip").line("wp.menu.upload.detail")));
            }
        }
        if (!transfer.isEmpty()) {
            group(lines, "wp.menu.transfer", Chat.join(transfer));
        }
        return lines.build();
    }

    private static void group(ChatLines lines, String labelKey, Component items) {
        lines.add(translatable(labelKey, GRAY));
        lines.line(text("  "), items);
    }
}
