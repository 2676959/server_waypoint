package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.FONT_COLOR;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;

public abstract class DialogWidget extends ShiftableClickableWidget {
    protected final TextRenderer textRenderer;
    protected final WidgetStack content;
    protected final Text title;
    protected final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    protected final PaddingBackground paddingBackground = new PaddingBackground(this.mainLayout, 6, 8, TRANSPARENT_BG_COLOR, FONT_COLOR, true);
    protected final WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);

    public DialogWidget(int x, int y, Text title, WidgetStack content, TextRenderer textRenderer) {
        super(x, y, textRenderer.getWidth(title), 0, title);
        this.textRenderer = textRenderer;
        this.title = title;
        this.content = content;
        this.mainLayout.addChild(new ScalableText(0, 0, this.title, 1.2F, FONT_COLOR, this.textRenderer), 0);
        this.mainLayout.addChild(content);
        List<ClickableWidget> buttons = this.createButtons();
        this.buttonRow.addClickable(buttons.get(0), 0);
        for (int i = 1; i < buttons.size(); i++) {
            this.buttonRow.addClickable(buttons.get(i));
        }
        this.mainLayout.addChild(buttonRow);
        this.buttonRow.setXOffset(this.mainLayout.getWidth());
        this.width = this.mainLayout.getWidth();
        this.height = this.mainLayout.getHeight();
    }

    abstract protected @Unmodifiable List<ClickableWidget> createButtons();

    @Override
    public int getWidth() {
        return this.mainLayout.getWidth();
    }

    @Override
    public int getHeight() {
        return this.mainLayout.getHeight();
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.mainLayout.setX(x);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.mainLayout.setY(y);
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        this.mainLayout.setXOffset(xOffset);
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        this.mainLayout.setYOffset(yOffset);
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        this.buttonRow.forEachChild(consumer);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (this.visible) {
            this.paddingBackground.render(context, mouseX, mouseY, deltaTicks);
            this.mainLayout.render(context, mouseX, mouseY, deltaTicks);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.visible && this.buttonRow.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
