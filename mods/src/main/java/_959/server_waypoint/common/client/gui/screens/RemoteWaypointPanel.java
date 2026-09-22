//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import _959.server_waypoint.common.client.RemoteClientCatalogs;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.ClientCommandUtils;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import _959.server_waypoint.common.client.gui.render.WaypointRowRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.util.ColorUtils.getSafeTextColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import java.util.*;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.*;

/** Read-only remote panel owned and rendered by the waypoint manager. */
final class RemoteWaypointPanel {
    private final Font font;
    private final RemoteClientCatalogs catalogs;
    private final long session;
    private final BrowserTree tree;
    private final WaypointDetailsWidget details;
    private final TranslucentButton teleportButton;
    private Map<RemoteServerId, CatalogReceiver.View> displayed = Map.of();
    private RemoteCatalogState displayedState;
    private RemoteWaypointKey selected;
    private String filter = "";
    private boolean reversed;
    private boolean grouped = true;
    private WaypointSorting.SortMode sortMode = WaypointSorting.SortMode.NAME;
    private String feedback = "waypoint.remote.gui.feedback";

    RemoteWaypointPanel(WaypointClientMod client, Font font) {
        this.font = font;
        this.catalogs = client.remoteCatalogs();
        this.session = catalogs.session();
        this.tree = new BrowserTree();
        this.details = new WaypointDetailsWidget(0, 0, 160, 160, font);
        this.teleportButton = new TranslucentButton(0, 0, 100, 16,
                Component.translatable("waypoint.remote.gui.teleport"), this::teleport, AnchorMode.OUTLINE);
        this.teleportButton.setTooltip(Tooltip.create(Component.translatable("waypoint.remote.gui.teleport_hint")));
    }

    void register(Consumer<AbstractWidget> register) {
        register.accept(tree);
        register.accept(details);
        register.accept(teleportButton);
    }

    void layout(int x, int y, int width, int height, int detailsX, int detailsY, int detailsWidth, int detailsHeight) {
        tree.setX(x);
        tree.setY(y);
        tree.setWidth(width);
        tree.setHeight(height);
        details.setX(detailsX);
        details.setY(detailsY);
        details.setWidth(detailsWidth);
        details.setHeight(Math.max(1, detailsHeight - 24));
        teleportButton.setX(detailsX);
        teleportButton.setY(detailsY + detailsHeight - 20);
        teleportButton.setVisualWidth(detailsWidth);
    }

    void setVisible(boolean visible) {
        tree.visible = tree.active = visible;
        details.visible = details.active = visible;
        teleportButton.visible = visible;
        teleportButton.active = visible && session == catalogs.session()
                && RemoteBrowserModel.prepare(catalogs, selected) != null;
    }

    void setOptions(String filter, boolean grouped, WaypointSorting.SortMode sortMode, boolean reversed) {
        this.filter = filter;
        this.grouped = grouped;
        this.sortMode = sortMode;
        this.reversed = reversed;
        rebuild();
    }

    private void rebuild() {
        displayed = catalogs.snapshot();
        displayedState = catalogs.state();
        teleportButton.setTooltip(Tooltip.create(Component.translatable(feedback)));
        var roots = RemoteBrowserModel.roots(displayed, filter, grouped, sortMode, reversed);
        // Filter/removal must not leave an actionable invisible selection.
        if (selected != null && !contains(roots, selected)) selected = null;
        tree.updateRoots(roots);
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
        teleportButton.active = teleportButton.visible && session == catalogs.session() && RemoteBrowserModel.prepare(catalogs, selected) != null;
    }

    private static boolean contains(List<RemoteBrowserModel.Node> nodes, RemoteWaypointKey key) {
        return nodes.stream().anyMatch(node -> key.equals(node.path().key()) || contains(node.children(), key));
    }

    void tick() {
        if (session != catalogs.session()) {
            MinecraftClientHelper.setScreen(null);
            return;
        }
        if (displayed != catalogs.snapshot() || displayedState != catalogs.state()) rebuild();
    }

    private void teleport() {
        var request = RemoteBrowserModel.prepare(catalogs, selected);
        if (session != catalogs.session() || request == null || !RemoteBrowserModel.isCurrent(catalogs, request)) {
            feedback = "waypoint.remote.gui.changed";
        } else if (ClientCommandUtils.sendCommand(request.command())) {
            MinecraftClientHelper.setScreen(null);
            return;
        } else {
            feedback = "waypoint.remote.gui.send_failed";
        }
        rebuild();
    }

    void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (!tree.visible) return;
        tree.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        details.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        teleportButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        Component status = Component.translatable("waypoint.remote.gui.status", Component.translatable(
                "waypoint.remote.state." + catalogs.state().name().toLowerCase(Locale.ROOT)));
        drawText(context, font, font.plainSubstrByWidth(status.getString(), tree.getWidth()),
                tree.getX(), tree.getY() + tree.getHeight() + 3, getColor(TEXT_MUTED));
        tree.renderHoveredTooltip(context, mouseX, mouseY);

    }

    // Local WaypointListWidget requires mutable local lists. Share its row presentation while keeping remote snapshots immutable.
    private final class BrowserTree extends TreeViewWidget<RemoteBrowserModel.Node> {
        private final Set<RemoteBrowserModel.Path> collapsed = new HashSet<>();
        private final ScalableText emptyMessage = new ScalableText(
                2, 2, Component.translatable("waypoint.remote.no_servers"), TEXT_MUTED, font);
        BrowserTree() { super(0, 0, 160, 160, 20, Component.translatable("waypoint.remote.title")); }
        @Override
        protected List<RemoteBrowserModel.Node> getChildren(RemoteBrowserModel.Node node) { return node.children(); }
        @Override
        protected boolean isExpanded(RemoteBrowserModel.Node node) { return !collapsed.contains(node.path()); }
        @Override
        protected void setExpanded(RemoteBrowserModel.Node node, boolean expanded) {
            if (expanded) collapsed.remove(node.path()); else collapsed.add(node.path());
        }
        private void renderHoveredTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
            var entry = getHoveredEntry();
            if (entry == null) return;
            var path = entry.value().path();
            String identity = path.server().value()
                    + (path.dimension() == null ? "" : " / " + path.dimension())
                    + (path.list() == null ? "" : " / [" + path.list() + "]")
                    + (path.waypoint() == null ? "" : " / [" + path.waypoint() + "]");
            var client = net.minecraft.client.Minecraft.getInstance();
            var lines = Tooltip.create(Component.literal(identity)).toCharSequence(client);
            // Use the cursor position, not the bounds of the entire scrollable tree.
            //? if >=1.21.6 {
            context.setTooltipForNextFrame(lines, mouseX, mouseY);
            //?} else {
            /*if (client.screen != null) client.screen.setTooltipForNextRenderPass(lines);
            *///?}
        }
        @Override
        protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int availableWidth = Math.max(1, getContentWidth() - 4);
            if (emptyMessage.getWidth() != availableWidth) {
                emptyMessage.setMaxWidth(availableWidth);
            }
            emptyMessage.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
        }
        @Override
        protected void renderEntry(GuiGraphicsExtractor context, TreeEntry<RemoteBrowserModel.Node> entry,
                                   boolean hovered, int rowY, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
            var node = entry.value();
            int indent = entry.depth() * 10;
            int textY = rowY + (20 - font.lineHeight) / 2 + 1;
            int textColor = getColor(node.state() == RemoteCatalogState.AVAILABLE ? TEXT_PRIMARY : TEXT_MUTED);
            var key = node.path().key();
            var view = displayed.get(node.path().server());
            var waypoint = key == null || view == null || view.snapshot() == null ? null
                    : view.snapshot().find(key).orElse(null);
            if (waypoint != null) {
                WaypointRowRenderer.background(context, rowY, contentWidth, 20, waypoint.rgb(), hovered, key.equals(selected));
                if (waypoint.global()) drawText(context, font, "*", indent + 6, textY, textColor);
                int badgeWidth = WaypointRowRenderer.initials(context, font, waypoint.initials(), indent + 15, textY - 1,
                        0xFF000000 | waypoint.rgb(), getSafeTextColor(waypoint.rgb()));
                int nameX = indent + 18 + badgeWidth;
                Component label = parseFormattedText(waypoint.displayName());
                if (!grouped) label = label.copy().append(Component.literal(" · " + key.serverId().value()
                        + " / " + key.dimensionName() + " / [" + key.listName() + "]"));
                drawText(context, font, label, nameX, textY, textColor, true);
            } else {
                if (hovered) {
                    context.fill(0, rowY, contentWidth, rowY + 20, getColor(ROW_HOVER_BACKGROUND));
                    renderOutline(context, 0, rowY, contentWidth, 20, getColor(FOCUS_RING));
                }
                texture(context, node.children().isEmpty() ? WidgetTextures.LIST_EMPTY
                        : isExpanded(node) ? WidgetTextures.LIST_EXPAND_ICON : WidgetTextures.LIST_COLLAPSE_ICON,
                        indent, rowY + 2, 0, 0, 16, 16, 16, 16);
                Component label;
                if (node.path().list() != null && view != null && view.snapshot() != null) {
                    var list = view.snapshot().dimensions().getOrDefault(node.path().dimension(), Map.of()).get(node.path().list());
                    label = list == null ? Component.literal(node.label()) : parseFormattedText(list.displayName());
                } else label = Component.literal(node.label());
                if (node.path().dimension() == null) label = label.copy().append(" · ").append(Component.translatable(
                        "waypoint.remote.state." + node.state().name().toLowerCase(Locale.ROOT)));
                drawText(context, font, label, indent + 18,
                        textY, textColor, true);
            }
        }
        @Override
        protected boolean onEntryClicked(TreeEntry<RemoteBrowserModel.Node> entry, double x, double y, int button) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
            var node = entry.value();
            selected = node.path().key();
            details.setRemoteSelection(selected, displayed.get(node.path().server()));
            teleportButton.active = teleportButton.visible && session == catalogs.session()
                    && RemoteBrowserModel.prepare(catalogs, selected) != null;
            return selected != null;
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { }
    }
}
