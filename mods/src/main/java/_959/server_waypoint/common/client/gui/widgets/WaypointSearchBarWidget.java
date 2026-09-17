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
    }
}
