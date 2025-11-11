package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.concurrent.CopyOnWriteArrayList;

import static _959.server_waypoint.common.server.WaypointServerMod.LOGGER;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class DimensionListWidget extends ClickableWidget {
    public static final ItemStack OVERWORLD_ICON = new ItemStack(Blocks.GRASS_BLOCK);
    public static final ItemStack THE_NETHER_ICON = new ItemStack(Blocks.RED_NETHER_BRICKS);
    public static final ItemStack THE_END_ICON = new ItemStack(Blocks.END_STONE);
    public static final ItemStack CUSTOM_DIMENSION_ICON = new ItemStack(Blocks.STRUCTURE_BLOCK);
    public static CopyOnWriteArrayList<String> dimensionNames = new CopyOnWriteArrayList<>();
    private static DimensionListCallback callback = (index) -> {};
    private final TextRenderer textRenderer;
    private final int textHeight = 10;
    private final int iconSize = 20;
    private static float scrolledPosition;
    private static int index;

    static {
        dimensionNames.add("minecraft:overworld");
        dimensionNames.add("minecraft:the_nether");
        dimensionNames.add("minecraft:the_end");
        dimensionNames.add("custom:dimension0");
        dimensionNames.add("custom:dimension1");
        dimensionNames.add("custom:dimension2");
        dimensionNames.add("custom:dimension3");
        dimensionNames.add("custom:dimension4");
        dimensionNames.add("custom:dimension5");
        dimensionNames.add("custom:dimension6");
        dimensionNames.add("custom:dimension7");
        dimensionNames.add("custom:dimension8");
        dimensionNames.add("custom:dimension9");
        dimensionNames.add("custom:dimension10");
        dimensionNames.add("custom:dimension11");
    }

    public static void setCallback(DimensionListCallback callback) {
        DimensionListWidget.callback = callback;
    }

    public DimensionListWidget(int x, int y, int width, int height, TextRenderer textRenderer) {
        super(x, y, width, height, Text.literal("Dimensions"));
        this.textRenderer = textRenderer;
        scrolledPosition = x;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int size = dimensionNames.size();
        int x = getX();
        // max 12 icons in the row
        if (size > 12) {
            float nextPosition = (float) (scrolledPosition + verticalAmount * 5);
            int lowerbound = x - (size - 12) * iconSize;
            scrolledPosition = Math.clamp(nextPosition, lowerbound, x);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = getX();
        int y = getY();
        int x2 = x + this.width;
        int y1 = y + textHeight;
        int y2 = y1 + this.iconSize;
        if (mouseX > x && mouseX < x2 && mouseY > y1 && mouseY < y2) {
            float relativePos = (float) (mouseX - scrolledPosition);
            int clickedIndex =  (int) relativePos / iconSize;
            if (clickedIndex < dimensionNames.size()) {
                index = clickedIndex;
                callback.onSelected(index);
                LOGGER.info("onClick: {}", index);
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();
        int x2 = x + width;
        int y2 = y + height;

        context.enableScissor(x, y, x2, y2);
        context.fill(x, y, x2, y2, 0x88000000);
        context.getMatrices().push();
        context.getMatrices().translate(0, y, 0);

        // render dimension name
        if (!dimensionNames.isEmpty()) {
            context.drawText(textRenderer, dimensionNames.get(index), x, 0, 0xFFFFFFFF, true);
        }

        int size = dimensionNames.size();
        context.getMatrices().translate(0, textHeight, 0);
        int y1 = y + textHeight;
        // render hover highlight background
        if ((mouseY <= y1 + this.iconSize) && (mouseY >= y1) && (mouseX <= x + size * iconSize) && (mouseX >= x)) {
            float relativePos = mouseX - scrolledPosition;
            int index =  (int) relativePos / iconSize;
            float highlightPos = scrolledPosition + index * iconSize;
            context.getMatrices().translate(highlightPos, 0, 0);
            context.fill(0, 0, iconSize, iconSize, 0x99FFFFFF);
            context.getMatrices().translate(-highlightPos, 0, 0);
        }
        context.getMatrices().translate(scrolledPosition, 0, 0);

        // render selected border
        context.drawBorder(index * iconSize, 0, iconSize, iconSize, 0xFFFFFFFF);
        // render dimension icons
        context.getMatrices().scale(1.25F, 1.25F, 1.0F);
        for (int i = 0; i < size; i++) {
            String dimensionName = dimensionNames.get(i);
            switch (dimensionName) {
                case MINECRAFT_OVERWORLD:
                    context.drawItem(OVERWORLD_ICON, i * 16, 0);
                    break;
                case MINECRAFT_THE_NETHER:
                    context.drawItem(THE_NETHER_ICON, i * 16, 0);
                    break;
                case MINECRAFT_THE_END:
                    context.drawItem(THE_END_ICON, i * 16, 0);
                    break;
                default:
                    context.drawItem(CUSTOM_DIMENSION_ICON, i * 16, 0);
            }
        }
        context.getMatrices().pop();
        context.disableScissor();

    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
