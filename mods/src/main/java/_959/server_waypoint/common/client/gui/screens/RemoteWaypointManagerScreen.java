//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.*;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.*;

/** Read-only manager branch. All local mutation/render widgets remain on the local screen. */
public final class RemoteWaypointManagerScreen extends MovementAllowedScreen {
    private final WaypointManagerScreen local;
    private final RemoteClientCatalogs catalogs;
    private final long session;
    private final BrowserTree tree = new BrowserTree();
    private final WaypointDetailsWidget details = new WaypointDetailsWidget(0, 0, 160, 160, font);
    private final WaypointSearchBarWidget search = new WaypointSearchBarWidget(
            0, 0, 160, Component.translatable("waypoint.search.entry"), font, this::filter);
    private final TranslucentButton localButton;
    private final TranslucentButton sortButton;
    private final TranslucentButton teleportButton;
    private Map<RemoteServerId, CatalogReceiver.View> displayed = Map.of();
    private RemoteCatalogState displayedState;
    private RemoteWaypointKey selected;
    private String filter = "";
    private boolean reversed;
    private String feedback = "waypoint.remote.gui.feedback";

    public RemoteWaypointManagerScreen(WaypointClientMod client, WaypointManagerScreen local) {
        super(Component.translatable("waypoint.remote.title"));
        this.local = local;
        this.catalogs = client.remoteCatalogs();
        this.session = catalogs.session();
        this.localButton = button("waypoint.remote.gui.local", this::onClose);
        this.sortButton = button("waypoint.sort.ascending", this::toggleSort);
        this.teleportButton = button("waypoint.remote.gui.teleport", this::confirmTeleport);
        this.teleportButton.setTooltip(Tooltip.create(Component.translatable("waypoint.remote.gui.teleport_hint")));
        this.acceptMovementKeys(false);
    }

    private TranslucentButton button(String key, Runnable action) {
        return new TranslucentButton(0, 0, 100, 16, Component.translatable(key), action::run, AnchorMode.OUTLINE);
    }

    @Override
    protected void init() {
        super.init();
        int left = getCenteredX();
        int top = getCenteredY();
        int half = (getContentWidth() - 8) / 2;
        localButton.setX(left);
        localButton.setY(top);
        localButton.setVisualWidth(half);
        sortButton.setX(left + half + 8);
        sortButton.setY(top);
        sortButton.setVisualWidth(half);
        search.setX(left + 2);
        search.setY(top + 27);
        search.setWidth(half - 4);
        tree.setX(left);
        tree.setY(top + 48);
        tree.setWidth(half);
        tree.setHeight(getContentHeight() - 74);
        details.setX(left + half + 8);
        details.setY(top + 26);
        details.setWidth(half);
        details.setHeight(getContentHeight() - 52);
        teleportButton.setX(left + half + 8);
        teleportButton.setY(top + getContentHeight() - 20);
        teleportButton.setVisualWidth(half);
        addRenderableWidget(localButton);
        addRenderableWidget(sortButton);
        addRenderableWidget(search);
        addRenderableWidget(tree);
        addRenderableWidget(details);
        addRenderableWidget(teleportButton);
        rebuild();
    }

    private void filter(String value) {
        filter = value;
        rebuild();
    }

    private void toggleSort() {
        reversed = !reversed;
        sortButton.setText(Component.translatable(reversed ? "waypoint.sort.descending" : "waypoint.sort.ascending"));
        rebuild();
    }

    private void rebuild() {
        displayed = catalogs.snapshot();
        displayedState = catalogs.state();
        var roots = RemoteBrowserModel.roots(displayed, filter, reversed);
        // Filter/removal must not leave an actionable invisible selection.
        if (selected != null && !contains(roots, selected)) selected = null;
        tree.setTooltip(null);
        tree.updateRoots(roots);
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
        teleportButton.active = session == catalogs.session() && RemoteBrowserModel.prepare(catalogs, selected) != null;
    }

    private static boolean contains(List<RemoteBrowserModel.Node> nodes, RemoteWaypointKey key) {
        return nodes.stream().anyMatch(node -> key.equals(node.path().key()) || contains(node.children(), key));
    }

    @Override
    public void tick() {
        super.tick();
        if (session != catalogs.session()) {
            MinecraftClientHelper.setScreen(this.minecraft, null);
            return;
        }
        if (displayed != catalogs.snapshot() || displayedState != catalogs.state()) rebuild();
    }

    private void confirmTeleport() {
        var confirmation = RemoteBrowserModel.prepare(catalogs, selected);
        if (session != catalogs.session() || confirmation == null) {
            feedback = "waypoint.remote.gui.changed";
            rebuild();
            return;
        }
        var key = confirmation.key();
        Component message = Component.translatable("waypoint.remote.gui.confirm",
                key.serverId().value(), key.dimensionName(), key.listName(), key.waypointName());
        MinecraftClientHelper.setScreen(this.minecraft, new ConfirmScreen(accepted -> {
            if (accepted && RemoteBrowserModel.isCurrent(catalogs, confirmation)) {
                if (ClientCommandUtils.sendCommand(confirmation.command())) {
                    // Existing command feedback supplies preparation, permission, transfer and failure results.
                    MinecraftClientHelper.setScreen(this.minecraft, null);
                    return;
                }
                feedback = "waypoint.remote.gui.send_failed";
            } else if (accepted) {
                feedback = "waypoint.remote.gui.changed";
            }
            MinecraftClientHelper.setScreen(this.minecraft, session == catalogs.session() ? this : null);
        }, Component.translatable("waypoint.remote.gui.teleport"), message));
    }

    @Override
    int getContentWidth() { return Math.max(220, Math.min(700, width - 24)); }

    @Override
    int getContentHeight() { return Math.max(140, Math.min(420, height - 32)); }

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        localButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        sortButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        search.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
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
        drawText(context, font, status, getCenteredX(), getCenteredY() + getContentHeight() - 16, getColor(TEXT_MUTED));
        Component hint = Component.translatable(feedback);
        drawText(context, font, font.plainSubstrByWidth(hint.getString(), getContentWidth()),
                getCenteredX(), getCenteredY() + getContentHeight() + 4, getColor(TEXT_MUTED), true);
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, session == catalogs.session() ? local : null);
    }

    private final class BrowserTree extends TreeViewWidget<RemoteBrowserModel.Node> {
        private final Set<RemoteBrowserModel.Path> collapsed = new HashSet<>();
        BrowserTree() { super(0, 0, 160, 160, 16, Component.translatable("waypoint.remote.title")); }
        @Override
        protected List<RemoteBrowserModel.Node> getChildren(RemoteBrowserModel.Node node) { return node.children(); }
        @Override
        protected boolean isExpanded(RemoteBrowserModel.Node node) { return !collapsed.contains(node.path()); }
        @Override
        protected void setExpanded(RemoteBrowserModel.Node node, boolean expanded) {
            if (expanded) collapsed.remove(node.path()); else collapsed.add(node.path());
        }
        @Override
        protected void onHoveredEntryChanged(TreeEntry<RemoteBrowserModel.Node> oldEntry,
                                             TreeEntry<RemoteBrowserModel.Node> newEntry) {
            if (newEntry == null) {
                setTooltip(null);
                return;
            }
            var path = newEntry.value().path();
            String identity = path.server().value()
                    + (path.dimension() == null ? "" : " / " + path.dimension())
                    + (path.list() == null ? "" : " / [" + path.list() + "]")
                    + (path.waypoint() == null ? "" : " / [" + path.waypoint() + "]");
            setTooltip(Tooltip.create(Component.literal(identity)));
        }
        @Override
        protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            drawText(context, font, Component.translatable("waypoint.remote.no_servers"), 2, 2, getColor(TEXT_MUTED));
        }
        @Override
        protected void renderEntry(GuiGraphicsExtractor context, TreeEntry<RemoteBrowserModel.Node> entry,
                                   boolean hovered, int rowY, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
            var node = entry.value();
            if (hovered || selected != null && selected.equals(node.path().key())) {
                context.fill(0, rowY, contentWidth, rowY + 16, getColor(CONTROL_HOVER_BACKGROUND));
            }
            Component label = Component.literal((node.children().isEmpty() ? "" : isExpanded(node) ? "− " : "+ ") + node.label());
            if (node.path().dimension() == null) label = Component.translatable(
                    "waypoint.remote.state." + node.state().name().toLowerCase(Locale.ROOT)).append(" · ").append(label);
            int x = 2 + entry.depth() * 8;
            drawText(context, font, font.plainSubstrByWidth(label.getString(), Math.max(1, contentWidth - x)), x, rowY + 3,
                    getColor(node.state() == RemoteCatalogState.AVAILABLE ? TEXT_PRIMARY : TEXT_MUTED), true);
        }
        @Override
        protected boolean onEntryClicked(TreeEntry<RemoteBrowserModel.Node> entry, double x, double y, int button) {
            if (button != 0) return false;
            var node = entry.value();
            selected = node.path().key();
            details.setRemoteSelection(selected, displayed.get(node.path().server()));
            teleportButton.active = RemoteBrowserModel.prepare(catalogs, selected) != null;
            return selected != null;
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { }
    }
}
