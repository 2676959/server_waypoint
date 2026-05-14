//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.util.MathHelper;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
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
    private final Screen parentScreen;
    private volatile @Unmodifiable List<String> dimensionNames = new ArrayList<>();
    private final Font textRenderer;
    private final PaddingBackground paddingBackground = new PaddingBackground(this, 7, 0, 10, 10, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
    private final IconButton addBtn = new IconButton(0, 0, 10, 10, Component.translatable("waypoint.add.button"), WaypointListWidget.ADD_ICON, this::openAddScreen);
    private final float itemIconScale;
    private final int textHeight;
    private final int iconSize;
    private boolean empty = true;

    public DimensionListWidget(int x, int y, int width, int iconSize, Screen parentScreen,Font textRenderer, DimensionListCallback callback) {
        super(x, y, width, textRenderer.lineHeight + 2 + iconSize, Component.literal("Dimensions list"));
        this.parentScreen = parentScreen;
        this.textRenderer = textRenderer;
        this.textHeight = textRenderer.lineHeight;
        this.callback = callback;
        this.iconSize = iconSize;
        this.itemIconScale = iconSize / 16F;
        scrolledPosition = 0;
        index = 0;
        this.addBtn.setPosition(x, y);
        this.addBtn.setXOffset(this.width - this.addBtn.getWidth());
    }

    public DimensionListWidget(int x, int y, int width, Screen parentScreen, Font textRenderer, DimensionListCallback callback) {
        this(x, y, width, 20, parentScreen, textRenderer, callback);
    }

    /**
     * reset all static states: scrolledPosition, index
     * */
    public static void resetStates() {
        scrolledPosition = 0;
        index = 0;
    }

    private void openAddScreen() {
        Minecraft.getInstance().setScreen(new WaypointAddScreen(parentScreen, getSelectedDimensionName(), ""));
    }

    @Override
    public void setHeight(int height) {}

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
    }

    public void setDimensionName(String dimensionName) {
        int index = this.dimensionNames.indexOf(dimensionName);
        if (index >= 0) {
            DimensionListWidget.index = index;
        }
    }

    public String getSelectedDimensionName() {
        return dimensionNames.get(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int size = dimensionNames.size();
        // max 12 icons in the row
        if (size > 12) {
            float nextPosition = (float) (scrolledPosition + verticalAmount * 5);
            int minScroll = -(size - 12) * iconSize;
            scrolledPosition = MathHelper.clamp(nextPosition, minScroll, 0);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (empty) return false;
        int x = getX();
        int y = getY();
        int x2 = x + this.width;
        int y1 = y + textHeight;
        int y2 = y1 + this.iconSize;
        if (mouseX > x && mouseX < x2 && mouseY > y1 && mouseY < y2) {
            float relativePos = (float) (mouseX - (x + scrolledPosition));
            int clickedIndex = (int) Math.floor(relativePos / iconSize);
            if (clickedIndex >= 0 && clickedIndex < dimensionNames.size()) {
                index = clickedIndex;
                callback.onSelected(dimensionNames.get(index));
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
        } else {
            return addBtn.mouseClicked(mouseX, mouseY, button);
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
        int x2 = x + width;
        int y2 = y + height;
        // render background
        paddingBackground.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        addBtn.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);

        context.enableScissor(x, y, x2, y2);
        push(context);
        translate(context, x, y);

        // render dimension name
        if (this.empty) {
            context.
            //$ gui_text_method_swap
            text
                    (textRenderer, WaypointListWidget.EMPTY_INFO_TEXT, 0, 0, 0xFFFFFFFF, true);
        } else {
            context.
            //$ gui_text_method_swap
            text
                    (textRenderer, dimensionNames.get(index), 0, 0, 0xFFFFFFFF, true);
            translate(context, 0, textHeight + 2);
            int size = dimensionNames.size();
            int y1 = y + textHeight;
            // render hover highlight background
            if ((mouseY < y1 + this.iconSize) && (mouseY > y1) && (mouseX < x + width) && (mouseX > x)) {
                float relativePos = mouseX - x - scrolledPosition;
                int hoverIndex = (int) (relativePos / iconSize);
                if (hoverIndex >= 0 && hoverIndex < size) {
                    float highlightPos = scrolledPosition + hoverIndex * iconSize;
                    translate(context, highlightPos, 0);
                    context.fill(0, 0, iconSize, iconSize, 0x99FFFFFF);
                    translate(context, -highlightPos, 0);
                }
            }
            // render selected border
            translate(context, scrolledPosition, 0);
            renderOutline(context, index * iconSize, 0, iconSize, iconSize, 0xFFFFFFFF);
            // render dimension icons
            scale(context, itemIconScale, itemIconScale);
            for (int i = 0; i < size; i++) {
                String dimensionName = dimensionNames.get(i);
                switch (dimensionName) {
                    case MINECRAFT_OVERWORLD:
                        context.
                        //$ gui_item_method_swap
                        item
                                (OVERWORLD_ICON, i * 16, 0);
                        break;
                    case MINECRAFT_THE_NETHER:
                        context.
                        //$ gui_item_method_swap
                        item
                                (THE_NETHER_ICON, i * 16, 0);
                        break;
                    case MINECRAFT_THE_END:
                        context.
                        //$ gui_item_method_swap
                        item
                                (THE_END_ICON, i * 16, 0);
                        break;
                    default:
                        context.
                        //$ gui_item_method_swap
                        item
                                (CUSTOM_DIMENSION_ICON, i * 16, 0);
                }
            }
        }
        pop(context);
        context.disableScissor();
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        addBtn.setX(x);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        addBtn.setY(y);
    }

    @Override
    public void setXOffset(int xOffest) {
        super.setXOffset(xOffest);
        addBtn.setXOffset(xOffest);
    }

    @Override
    public void setYOffset(int yOffest) {
        super.setYOffset(yOffest);
        addBtn.setYOffset(yOffest);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }

    @Override
    public void setVisualHeight(int height) {
        setHeight(height - (this.paddingBackground.getVisualHeight() - getHeight()));
    }

    @Override
    public void setVisualWidth(int width) {
        setWidth(width - (this.paddingBackground.getVisualWidth() - getWidth()));
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

    @Override
    public void setPaddedX(int x) {
        this.paddingBackground.setPaddedX(x);
    }

    @Override
    public void setPaddedY(int y) {
        this.paddingBackground.setPaddedY(y);
    }
}
