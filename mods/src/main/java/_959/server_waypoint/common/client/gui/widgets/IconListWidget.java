//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.IconListLayout;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.render.PaddingBackground;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.FOCUS_RING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;

public abstract class IconListWidget<T> extends ShiftableClickableWidget implements Padding, Expandable {
    private float scrolledPosition;
    private int index;
    private final java.util.function.Consumer<T> callback;
    private List<T> entries = List.of();
    private final PaddingBackground paddingBackground;
    private final float itemIconScale;
    private final int iconSize;
    private final IconListLayout iconLayout;
    private boolean empty = true;

    protected IconListWidget(int x, int y, int width, int height, int iconSize,
            java.util.function.Consumer<T> callback, Orientation orientation, Direction direction,
            int iconSpacing, int verticalPadding, int horizontalPadding, Component message) {
        super(x, y, width, Math.max(height, iconSize), message);
        this.setX(x);
        this.setY(y);
        if (verticalPadding < 0 || horizontalPadding < 0) {
            throw new IllegalArgumentException("Icon list padding cannot be negative");
        }
        this.callback = callback;
        this.iconSize = iconSize;
        this.itemIconScale = iconSize / 16F;
        this.iconLayout = new IconListLayout(iconSize, orientation, direction, iconSpacing);
        this.paddingBackground = new PaddingBackground(
                this,
                verticalPadding,
                horizontalPadding,
                PANEL_BACKGROUND,
                BORDER,
                false
        );
        scrolledPosition = 0;
        index = 0;
    }

    public void resetSelection() {
        scrolledPosition = 0;
        index = 0;
    }

    public int preferredHeight() {
        if (getOrientation() == Orientation.HORIZONTAL) return iconSize;
        return Math.max(iconSize, entries.size() * (iconSize + getIconSpacing()) - getIconSpacing());
    }

    @Override
    public void setHeight(int height) {
        this.height = Math.max(height, this.iconSize);
        this.clampScrollPosition();
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        this.clampScrollPosition();
    }

    public void setEntries(List<T> entries) {
        T selected = getSelectedEntry();
        this.entries = List.copyOf(entries);
        this.empty = entries.isEmpty();
        index = selected == null ? 0 : Math.max(0, this.entries.indexOf(selected));
        this.clampScrollPosition();
    }

    public void setSelectedEntry(T entry) {
        int selected = entry == null ? -1 : entries.indexOf(entry);
        if (selected >= 0) index = selected;
    }

    public T getSelectedEntry() {
        return index < entries.size() ? entries.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.active || !this.visible || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        IconListLayout.Bounds viewport = this.getIconViewport();
        scrolledPosition = this.iconLayout.scrollBy(scrolledPosition, verticalAmount * 5, this.entries.size(), viewport);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || empty || button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        int clickedIndex = this.iconLayout.iconIndexAt(
                mouseX - this.getX(),
                mouseY - this.getY(),
                scrolledPosition,
                this.entries.size(),
                this.getIconViewport()
        );
        if (clickedIndex >= 0) {
            index = clickedIndex;
            callback.accept(entries.get(index));
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        // render background
        paddingBackground.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);

        this.setTooltip(null);
        if (this.empty) {
            return;
        }

        IconListLayout.Bounds viewport = this.getIconViewport();
        if (viewport.width() <= 0 || viewport.height() <= 0) {
            return;
        }

        context.enableScissor(x + viewport.x(), y + viewport.y(), x + viewport.x() + viewport.width(), y + viewport.y() + viewport.height());
        push(context);
        translate(context, x, y);

        int hoverIndex = this.active
                ? this.iconLayout.iconIndexAt(mouseX - x, mouseY - y, scrolledPosition, this.entries.size(), viewport)
                : -1;
        if (hoverIndex >= 0) {
            Minecraft client = Minecraft.getInstance();
            var lines = Tooltip.create(entryLabel(entries.get(hoverIndex))).toCharSequence(client);
            // Anchor the entry label to the cursor, not the entire scrollable icon rail.
            //? if >=1.21.6 {
            context.setTooltipForNextFrame(lines, mouseX, mouseY);
            //?} else {
            /*if (client.screen != null) client.screen.setTooltipForNextRenderPass(lines);
            *///?}
            this.renderIconBackground(context, hoverIndex, viewport, getColor(ROW_HOVER_BACKGROUND), false);
        }
        this.renderIconBackground(context, index, viewport, getColor(this.active ? FOCUS_RING : BORDER), true);

        for (int i = 0; i < this.entries.size(); i++) {
            IconListLayout.Position position = this.iconLayout.iconPosition(i, scrolledPosition, viewport);
            push(context);
            translate(context, position.x(), position.y());
            scale(context, itemIconScale, itemIconScale);
            this.drawIcon(context, this.entries.get(i));
            pop(context);
        }

        pop(context);
        context.disableScissor();
    }

    public Orientation getOrientation() {
        return this.iconLayout.orientation();
    }

    public Direction getDirection() {
        return this.iconLayout.direction();
    }

    public int getIconSpacing() {
        return this.iconLayout.iconSpacing();
    }

    private void renderIconBackground(GuiGraphicsExtractor context, int iconIndex, IconListLayout.Bounds viewport, int color, boolean outline) {
        IconListLayout.Position position = this.iconLayout.iconPosition(iconIndex, scrolledPosition, viewport);
        push(context);
        translate(context, position.x(), position.y());
        if (outline) {
            renderOutline(context, 0, 0, this.iconSize, this.iconSize, color);
        } else {
            context.fill(0, 0, this.iconSize, this.iconSize, color);
        }
        pop(context);
    }

    protected Component entryLabel(T entry) { return Component.literal(entry.toString()); }

    protected abstract void drawIcon(GuiGraphicsExtractor context, T entry);

    private IconListLayout.Bounds getIconViewport() {
        return this.iconLayout.viewport(this.width, this.height, 0);
    }

    private void clampScrollPosition() {
        scrolledPosition = this.iconLayout.clampScroll(scrolledPosition, this.entries.size(), this.getIconViewport());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }

    @Override
    public void setVisualHeight(int height) {
        setHeight(height - (this.paddingBackground.getPaddedHeight()));
    }

    @Override
    public void setVisualWidth(int width) {
        setWidth(width - (this.paddingBackground.getPaddedWidth()));
    }

    @Override
    public int getVisualHeight() {
        return this.paddingBackground.getVisualHeight();
    }

    @Override
    public int getVisualWidth() {
        return this.paddingBackground.getVisualWidth();
    }

    @Override
    public int getVisualX() {
        return this.paddingBackground.getVisualX();
    }

    @Override
    public int getVisualY() {
        return this.paddingBackground.getVisualY();
    }
}
