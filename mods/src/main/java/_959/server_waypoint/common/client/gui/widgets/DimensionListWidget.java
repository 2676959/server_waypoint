//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.api.DimensionListCallback;
import _959.server_waypoint.common.client.gui.layout.DimensionIconLayout;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawItem;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.FOCUS_RING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class DimensionListWidget extends ShiftableClickableWidget implements Padding, Expandable {
    public static final ItemStack OVERWORLD_ICON = new ItemStack(Blocks.GRASS_BLOCK);
    public static final ItemStack THE_NETHER_ICON = new ItemStack(Blocks.RED_NETHER_BRICKS);
    public static final ItemStack THE_END_ICON = new ItemStack(Blocks.END_STONE);
    public static final ItemStack CUSTOM_DIMENSION_ICON = new ItemStack(Blocks.STRUCTURE_BLOCK);
    private static final int DEFAULT_VERTICAL_PADDING = 3;
    private static final int DEFAULT_HORIZONTAL_PADDING = 4;
    private static float scrolledPosition;
    private static int index;
    private final DimensionListCallback callback;
    private @Unmodifiable List<String> dimensionNames = List.of();
    private final PaddingBackground paddingBackground;
    private final float itemIconScale;
    private final int iconSize;
    private final DimensionIconLayout iconLayout;
    private boolean empty = true;

    public DimensionListWidget(int x, int y, int width, int iconSize, Screen parentScreen, Font textRenderer, DimensionListCallback callback) {
        this(x, y, width, iconSize, iconSize, parentScreen, textRenderer, callback, Orientation.HORIZONTAL, Direction.FORWARD);
    }

    public DimensionListWidget(int x, int y, int width, int iconSize, Screen parentScreen, Font textRenderer, DimensionListCallback callback, Orientation orientation, Direction direction) {
        this(x, y, width, iconSize, iconSize, parentScreen, textRenderer, callback, orientation, direction);
    }

    /**
     * Creates a zero-gap dimension list whose icon strip follows the supplied layout flow.
     */
    public DimensionListWidget(int x, int y, int width, int height, int iconSize, Screen parentScreen, Font textRenderer, DimensionListCallback callback, Orientation orientation, Direction direction) {
        this(x, y, width, height, iconSize, parentScreen, textRenderer, callback, orientation, direction, 0);
    }

    /**
     * Creates a dimension list whose icon strip follows the supplied layout flow and uses the
     * given non-negative spacing between adjacent icons.
     */
    public DimensionListWidget(
            int x,
            int y,
            int width,
            int height,
            int iconSize,
            Screen parentScreen,
            Font textRenderer,
            DimensionListCallback callback,
            Orientation orientation,
            Direction direction,
            int iconSpacing
    ) {
        this(
                x,
                y,
                width,
                height,
                iconSize,
                parentScreen,
                textRenderer,
                callback,
                orientation,
                direction,
                iconSpacing,
                DEFAULT_VERTICAL_PADDING,
                DEFAULT_HORIZONTAL_PADDING
        );
    }

    /**
     * Creates a dimension list with custom non-negative symmetric padding around its icon strip.
     */
    public DimensionListWidget(
            int x,
            int y,
            int width,
            int height,
            int iconSize,
            Screen parentScreen,
            Font textRenderer,
            DimensionListCallback callback,
            Orientation orientation,
            Direction direction,
            int iconSpacing,
            int verticalPadding,
            int horizontalPadding
    ) {
        super(x, y, width, Math.max(height, iconSize), Component.literal("Dimensions list"));
        if (verticalPadding < 0 || horizontalPadding < 0) {
            throw new IllegalArgumentException("Dimension list padding cannot be negative");
        }
        this.callback = callback;
        this.iconSize = iconSize;
        this.itemIconScale = iconSize / 16F;
        this.iconLayout = new DimensionIconLayout(iconSize, orientation, direction, iconSpacing);
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

    public DimensionListWidget(int x, int y, int width, Screen parentScreen, Font textRenderer, DimensionListCallback callback) {
        this(x, y, width, 20, parentScreen, textRenderer, callback);
    }

    public DimensionListWidget(int x, int y, int width, Screen parentScreen, Font textRenderer, DimensionListCallback callback, Orientation orientation, Direction direction) {
        this(x, y, width, 20, parentScreen, textRenderer, callback, orientation, direction);
    }

    /**
     * reset all static states: scrolledPosition, index
     * */
    public static void resetStates() {
        scrolledPosition = 0;
        index = 0;
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

    /**
     * updates the reference of {@link #dimensionNames}, if newDimensionNames is empty only clears the current list
     */
    public void updateDimensionNames(@Unmodifiable List<String> newDimensionNames) {
        if (newDimensionNames.isEmpty()) {
            this.empty = true;
            index = 0;
            scrolledPosition = 0;
        } else {
            this.empty = false;
            if (index >= newDimensionNames.size()) {
                index = 0;
            }
        }
        this.dimensionNames = newDimensionNames;
        this.clampScrollPosition();
    }

    public void setDimensionName(String dimensionName) {
        int index = this.dimensionNames.indexOf(dimensionName);
        if (index >= 0) {
            DimensionListWidget.index = index;
        }
    }

    @NotNull
    public String getSelectedDimensionName() {
        if (index < 0 || index >= dimensionNames.size()) {
            WaypointClientMod.LOGGER.info("no dimensions have found currently");
            return "none";
        }
        return dimensionNames.get(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.active) {
            return false;
        }
        DimensionIconLayout.Bounds viewport = this.getIconViewport();
        scrolledPosition = this.iconLayout.scrollBy(scrolledPosition, verticalAmount * 5, this.dimensionNames.size(), viewport);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || empty) return false;
        int clickedIndex = this.iconLayout.iconIndexAt(
                mouseX - this.getX(),
                mouseY - this.getY(),
                scrolledPosition,
                this.dimensionNames.size(),
                this.getIconViewport()
        );
        if (clickedIndex >= 0) {
            index = clickedIndex;
            callback.onSelected(dimensionNames.get(index));
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

        if (this.empty) {
            return;
        }

        DimensionIconLayout.Bounds viewport = this.getIconViewport();
        if (viewport.width() <= 0 || viewport.height() <= 0) {
            return;
        }

        context.enableScissor(x + viewport.x(), y + viewport.y(), x + viewport.x() + viewport.width(), y + viewport.y() + viewport.height());
        push(context);
        translate(context, x, y);

        int hoverIndex = this.active
                ? this.iconLayout.iconIndexAt(mouseX - x, mouseY - y, scrolledPosition, this.dimensionNames.size(), viewport)
                : -1;
        if (hoverIndex >= 0) {
            this.renderIconBackground(context, hoverIndex, viewport, getColor(ROW_HOVER_BACKGROUND), false);
        }
        this.renderIconBackground(context, index, viewport, getColor(this.active ? FOCUS_RING : BORDER), true);

        for (int i = 0; i < this.dimensionNames.size(); i++) {
            DimensionIconLayout.Position position = this.iconLayout.iconPosition(i, scrolledPosition, viewport);
            push(context);
            translate(context, position.x(), position.y());
            scale(context, itemIconScale, itemIconScale);
            this.drawDimensionIcon(context, this.dimensionNames.get(i));
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

    private void renderIconBackground(GuiGraphicsExtractor context, int iconIndex, DimensionIconLayout.Bounds viewport, int color, boolean outline) {
        DimensionIconLayout.Position position = this.iconLayout.iconPosition(iconIndex, scrolledPosition, viewport);
        push(context);
        translate(context, position.x(), position.y());
        if (outline) {
            renderOutline(context, 0, 0, this.iconSize, this.iconSize, color);
        } else {
            context.fill(0, 0, this.iconSize, this.iconSize, color);
        }
        pop(context);
    }

    private void drawDimensionIcon(GuiGraphicsExtractor context, String dimensionName) {
        switch (dimensionName) {
            case MINECRAFT_OVERWORLD:
                drawItem(context, OVERWORLD_ICON, 0, 0);
                break;
            case MINECRAFT_THE_NETHER:
                drawItem(context, THE_NETHER_ICON, 0, 0);
                break;
            case MINECRAFT_THE_END:
                drawItem(context, THE_END_ICON, 0, 0);
                break;
            default:
                drawItem(context, CUSTOM_DIMENSION_ICON, 0, 0);
        }
    }

    private DimensionIconLayout.Bounds getIconViewport() {
        return this.iconLayout.viewport(this.width, this.height, 0);
    }

    private void clampScrollPosition() {
        scrolledPosition = this.iconLayout.clampScroll(scrolledPosition, this.dimensionNames.size(), this.getIconViewport());
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
