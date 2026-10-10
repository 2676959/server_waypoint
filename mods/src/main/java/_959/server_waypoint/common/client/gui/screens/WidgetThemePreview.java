//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The theme editor's preview panel, the right column of the screen: a {@link SettingsListWidget} that
 * paints the panel and holds a "Preview" header. The screen registers its widgets through
 * {@link #visitWidgets} and draws it once, with {@link #render}, after the key editor.
 */
final class WidgetThemePreview {
    private final SettingsListWidget list;

    WidgetThemePreview(Font font) {
        this.list = new SettingsListWidget(font);
        this.list.setEntries(List.of(
                new SettingsListWidget.Header(Component.translatable("server_waypoint.theme.preview.title"))));
    }

    /** The list that scrolls the preview, for Tab and focus handling. */
    SettingsListWidget list() {
        return this.list;
    }

    /** Sizes and places the panel by its visual bounds, outline included. */
    void setBounds(int visualX, int visualY, int visualWidth, int visualHeight) {
        this.list.setVisualWidth(visualWidth);
        this.list.setVisualHeight(visualHeight);
        this.list.setPosition(visualX + SettingsListWidget.PANEL_PADDING, visualY + SettingsListWidget.PANEL_PADDING);
    }

    /** Visits the widgets of the preview's rows, then the list, in the order the screen registers them. */
    void visitWidgets(Consumer<AbstractWidget> consumer) {
        this.list.visitWidgets(consumer);
    }

    /** Whether the preview takes input: not while the color picker is open. */
    void setActive(boolean active) {
        this.list.active = active;
    }

    /**
     * Draws the panel and its rows. {@code marked} is the key whose uses are marked, or null for none.
     */
    void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, @Nullable WidgetThemeVariable marked) {
        this.list.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
    }
}
