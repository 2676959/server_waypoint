//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Consumer;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawItem;

/** A bottom-anchored remote server icon rail, keyed by stable server identity. */
public final class ServerListWidget extends IconListWidget<RemoteServerId> {
    private Map<RemoteServerId, CatalogReceiver.View> servers = Map.of();

    public ServerListWidget(int iconSize, int gap, Consumer<RemoteServerId> callback) {
        super(0, 0, iconSize, iconSize, iconSize, callback, LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.REVERSE, gap, 0, 0, Component.translatable("waypoint.remote.title"));
    }

    public void setServers(Map<RemoteServerId, CatalogReceiver.View> servers) {
        this.servers = servers;
        setEntries(servers.keySet().stream().sorted(Comparator.comparing(RemoteServerId::value)).toList());
    }

    @Override
    protected Component entryLabel(RemoteServerId server) {
        return Component.literal(servers.get(server).displayName() + " [" + server.value() + "]");
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
