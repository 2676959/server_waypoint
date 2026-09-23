//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.DimensionListCallback;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class DimensionListWidget extends IconListWidget<String> {
    public static final ItemStack OVERWORLD_ICON = new ItemStack(Blocks.GRASS_BLOCK);
    public static final ItemStack THE_NETHER_ICON = new ItemStack(Blocks.RED_NETHER_BRICKS);
    public static final ItemStack THE_END_ICON = new ItemStack(Blocks.END_STONE);
    public static final ItemStack CUSTOM_DIMENSION_ICON = new ItemStack(Blocks.STRUCTURE_BLOCK);
    private static final int DEFAULT_VERTICAL_PADDING = 3;
    private static final int DEFAULT_HORIZONTAL_PADDING = 4;
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
        super(x, y, width, height, iconSize, callback::onSelected, orientation, direction,
                iconSpacing, verticalPadding, horizontalPadding, Component.translatable("waypoint.dimension.show_selected"));
    }

    public DimensionListWidget(int x, int y, int width, Screen parentScreen, Font textRenderer, DimensionListCallback callback) {
        this(x, y, width, 20, parentScreen, textRenderer, callback);
    }

    public DimensionListWidget(int x, int y, int width, Screen parentScreen, Font textRenderer, DimensionListCallback callback, Orientation orientation, Direction direction) {
        this(x, y, width, 20, parentScreen, textRenderer, callback, orientation, direction);
    }

    public void updateDimensionNames(List<String> names) { setEntries(names); }

    public void setDimensionName(String name) { setSelectedEntry(name); }

    public String getSelectedDimensionName() {
        return getSelectedEntry() == null ? "none" : getSelectedEntry();
    }

    @Override
    protected void drawIcon(GuiGraphicsExtractor context, String dimensionName) {
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

}
