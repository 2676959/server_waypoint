package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.block.Blocks;
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
    public static CopyOnWriteArrayList<String> dimensionNames = new CopyOnWriteArrayList<>();
    private static DimensionListCallback callback = (index) -> {};
    private final TextRenderer textRenderer;
    private final int iconSize = 20;
    private float scrolledPosition;
    private int index;

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
            if (nextPosition >= x) {
                scrolledPosition = x;
            } else if (nextPosition < lowerbound) {
                scrolledPosition = lowerbound;
            }  else {
                scrolledPosition = nextPosition;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        float relativePos = (float) (mouseX - scrolledPosition);
        int clickedIndex =  (int) relativePos / iconSize;
        if (clickedIndex < dimensionNames.size()) {
            this.index = clickedIndex;
            callback.onSelected(this.index);
            LOGGER.info("onClick: {}", this.index);
        }
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

        if (!dimensionNames.isEmpty()) {
            context.drawText(textRenderer, dimensionNames.get(this.index), x, 0, 0xFFFFFFFF, true);
        }

        int size = dimensionNames.size();
        int textHeight = 10;
        context.getMatrices().translate(0, textHeight, 0);
        if ((mouseY <= y2) && (mouseY >= y + textHeight) && (mouseX <= x + size * iconSize) && (mouseX >= x)) {
            float relativePos = mouseX - scrolledPosition;
            int index =  (int) relativePos / iconSize;
            float highlightPos = scrolledPosition + index * iconSize;
            context.getMatrices().translate(highlightPos, 0, 0);
            context.fill(0, 0, iconSize, iconSize, 0x99FFFFFF);
            context.getMatrices().translate(-highlightPos, 0, 0);
        }
        context.getMatrices().translate(scrolledPosition, 0, 0);
        context.drawBorder(index * iconSize, 0, iconSize, iconSize, 0xFFFFFFFF);

        context.getMatrices().scale(1.25F, 1.25F, 1.0F);

        for (int i = 0; i < size; i++) {
            String dimensionName = dimensionNames.get(i);
            switch (dimensionName) {
                case MINECRAFT_OVERWORLD:
                    context.drawItem(new ItemStack(Blocks.GRASS_BLOCK), i * 16, 0);
                    break;
                case MINECRAFT_THE_NETHER:
                    context.drawItem(new ItemStack(Blocks.RED_NETHER_BRICKS), i * 16, 0);
                    break;
                case MINECRAFT_THE_END:
                    context.drawItem(new ItemStack(Blocks.END_STONE), i * 16, 0);
                    break;
                default:
                    context.drawItem(new ItemStack(Blocks.STRUCTURE_BLOCK), i * 16, 0);
            }
        }
        context.getMatrices().pop();
        context.disableScissor();

    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
