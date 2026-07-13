//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
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

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawItem;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.scale;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class DimensionListWidget extends ShiftableClickableWidget implements Padding, Expandable {
    public static final ItemStack OVERWORLD_ICON = new ItemStack(Blocks.GRASS_BLOCK);
    public static final ItemStack THE_NETHER_ICON = new ItemStack(Blocks.RED_NETHER_BRICKS);
    public static final ItemStack THE_END_ICON = new ItemStack(Blocks.END_STONE);
    public static final ItemStack CUSTOM_DIMENSION_ICON = new ItemStack(Blocks.STRUCTURE_BLOCK);
    private static float scrolledPosition;
    private static int index;
    private final DimensionListCallback callback;
    private @Unmodifiable List<String> dimensionNames = List.of();
    private final PaddingBackground paddingBackground = new PaddingBackground(this, 7, 0, 10, 10, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
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
     * Creates a dimension list whose icon strip follows the supplied layout flow.
     */
    public DimensionListWidget(int x, int y, int width, int height, int iconSize, Screen parentScreen, Font textRenderer, DimensionListCallback callback, Orientation orientation, Direction direction) {
        super(x, y, width, Math.max(height, iconSize), Component.literal("Dimensions list"));
        this.callback = callback;
        this.iconSize = iconSize;
        this.itemIconScale = iconSize / 16F;
        this.iconLayout = new DimensionIconLayout(iconSize, orientation, direction);
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
        DimensionIconLayout.Bounds viewport = this.getIconViewport();
        scrolledPosition = this.iconLayout.scrollBy(scrolledPosition, verticalAmount * 5, this.dimensionNames.size(), viewport);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (empty) return false;
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

        int hoverIndex = this.iconLayout.iconIndexAt(mouseX - x, mouseY - y, scrolledPosition, this.dimensionNames.size(), viewport);
        if (hoverIndex >= 0) {
            this.renderIconBackground(context, hoverIndex, viewport, 0x99FFFFFF, false);
        }
        this.renderIconBackground(context, index, viewport, 0xFFFFFFFF, true);

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
