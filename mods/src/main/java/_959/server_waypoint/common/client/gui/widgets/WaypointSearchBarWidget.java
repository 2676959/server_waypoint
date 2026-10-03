package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.AnchorMode;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class WaypointSearchBarWidget extends TranslucentTextField {
    private final Consumer<String> searchQueryConsumer;

    public WaypointSearchBarWidget(int x, int y, int width, Component text, Font textRenderer, Consumer<String> searchQueryConsumer) {
        super(x, y, width, text, textRenderer, AnchorMode.OUTLINE);
        this.searchQueryConsumer = Objects.requireNonNull(searchQueryConsumer);
        this.setMaxLength(64);
        this.setResponder(this.searchQueryConsumer);
        // The themed placeholder, not vanilla's fixed dark gray hint, which is unreadable on the bare panel.
        this.setPlaceholder(() -> Component.translatable("waypoint.search.hint"));
    }

    /**
     * The manager's list panel behind the search field already paints a fill, so at rest the field
     * paints none of its own; a second fill would stack on the panel. Hover and the disabled look still fill.
     */
    @Override
    protected int surfaceColor() {
        return WidgetThemeState.controlBackground(this.active, isHovered(), false);
    }
}
