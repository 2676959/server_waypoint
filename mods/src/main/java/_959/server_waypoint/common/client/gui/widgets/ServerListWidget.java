//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import _959.server_waypoint.text.feedback.RemoteRefs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Consumer;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawItem;

/** A remote server icon rail above the manager controls, keyed by stable server identity. */
public final class ServerListWidget extends IconListWidget<RemoteServerId> {
    private Map<RemoteServerId, CatalogReceiver.View> servers = Map.of();

    public ServerListWidget(int iconSize, int gap, Consumer<RemoteServerId> callback) {
        super(0, 0, iconSize, iconSize, iconSize, callback, LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD, gap, 0, 0, Component.translatable("waypoint.remote.title"));
    }

    /** The theme role used by degraded server icon badges. */
    public static WidgetThemeVariable stateColor(RemoteCatalogState state) {
        return switch (state) {
            case AVAILABLE -> WidgetThemeVariable.TEXT_MUTED;
            case STALE -> WidgetThemeVariable.WARNING;
            case UNAVAILABLE, UNAUTHORIZED -> WidgetThemeVariable.DANGER;
        };
    }

    /** Available servers need no badge; degraded servers show their state color. */
    static @Nullable WidgetThemeVariable badgeColor(RemoteCatalogState state) {
        return state == RemoteCatalogState.AVAILABLE ? null : stateColor(state);
    }

    public static String stateTranslationKey(RemoteCatalogState state) {
        return "waypoint.remote.state." + state.name().toLowerCase(Locale.ROOT);
    }

    public void setServers(Map<RemoteServerId, CatalogReceiver.View> servers) {
        this.servers = servers;
        setEntries(servers.keySet().stream().sorted(Comparator.comparing(RemoteServerId::value)).toList());
    }

    @Override
    protected Component entryLabel(RemoteServerId server) {
        CatalogReceiver.View view = servers.get(server);
        int stateRgb = RemoteRefs.stateColor(view.state()).value();
        // Vanilla tooltip splitting treats the newline as a line break.
        return Component.literal(view.displayName() + " [" + server.value() + "]\n")
                .append(Component.translatable(stateTranslationKey(view.state()))
                        .withStyle(style -> style.withColor(stateRgb)));
    }

    @Override
    protected @Nullable WidgetThemeVariable entryBadgeColor(RemoteServerId server) {
        return badgeColor(servers.get(server).state());
    }

    @Override
    protected void drawIcon(GuiGraphicsExtractor context, RemoteServerId server) {
        //$ resource_location_type_swap
        Identifier
        id =
                //$ resource_location_type_swap
                Identifier
                .tryParse(servers.get(server).iconItem());
        var item = id == null ? Items.COMPASS : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.COMPASS);
        drawItem(context, new ItemStack(item == Items.AIR ? Items.COMPASS : item), 0, 0);
    }
}
