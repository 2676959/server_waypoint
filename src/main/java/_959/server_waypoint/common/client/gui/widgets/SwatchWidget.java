package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.util.ColorUtils.VANILLA_COLORS;

public class SwatchWidget extends ShiftableClickableWidget implements Colorable {
    private ColorPickerCallBack confirmCallback;
    private static final int BG_PADDING_X = 10;
    private static final int BG_PADDING_Y = 6;
    private final WidgetStack colorRow0 = new WidgetStack(0, 0, 1);
    private final WidgetStack colorRow1 = new WidgetStack(0, 0, 1);
//    private final WidgetStack colorRow2 = new WidgetStack(0, 0, 2);
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 0, true, false);
    private final RGBColorPicker rgbColorPicker = new RGBColorPicker(0, 0, 120, 11, this::rgbColorPickerCallback);
    private final HSVColorPicker hsvColorPicker = new HSVColorPicker(0, 0, 120, 11, this::hsvColorPickerCallback);
    private final IntegerField rEntry;
    private final IntegerField gEntry;
    private final IntegerField bEntry;
    private final IntegerField hEntry;
    private final IntegerField sEntry;
    private final IntegerField vEntry;
    private final ColorSquareButton currentColorButton = new ColorSquareButton(0, 0, 21, 0, false, () -> this.confirmCallback.onColorUpdate(this.currentColorButton.getColor()));
    private final ColorSquareButton previousColorButton = new ColorSquareButton(0, 0, 21, 0, false, () -> {});
    private Element focused = this.currentColorButton;

    public SwatchWidget(int x, int y, TextRenderer textRenderer, ColorPickerCallBack confirmCallback) {
        super(x, y, 0, 0, Text.of("Swatch"));
        this.confirmCallback = confirmCallback;
        WidgetStack slidersRow = new WidgetStack(0, 0, 0);
        WidgetStack labelCol = new WidgetStack(0, 0, 2, true, false);
        WidgetStack pickerCol = new WidgetStack(0, 0, 0, true, false);
        WidgetStack integerFieldCol = new WidgetStack(0, 0, 2, true, false);

        ScalableText hLabel = new ScalableText(0, 0, Text.of("H"), 0xFFFFFFFF, textRenderer);
        ScalableText sLabel = new ScalableText(0, 0, Text.of("S"), 0xFFFFFFFF, textRenderer);
        ScalableText vLabel = new ScalableText(0, 0, Text.of("V"), 0xFFFFFFFF, textRenderer);
        ScalableText rLabel = new ScalableText(0, 0, Text.of("R"), 0xFFFF0000, textRenderer);
        ScalableText gLabel = new ScalableText(0, 0, Text.of("G"), 0xFF00FF00, textRenderer);
        ScalableText bLabel = new ScalableText(0, 0, Text.of("B"), 0xFF0000FF, textRenderer);

        this.hEntry = new IntegerField(0, 0, 21, 0, 360, 0, Text.of("H"), textRenderer);
        this.sEntry = new IntegerField(0, 0, 21, 0, 100, 0, Text.of("S"), textRenderer);
        this.vEntry = new IntegerField(0, 0, 21, 0, 100, 0, Text.of("V"), textRenderer);
        this.rEntry = new IntegerField(0, 0, 21, 0, 255, 0, Text.of("R"), textRenderer);
        this.gEntry = new IntegerField(0, 0, 21, 0, 255, 0, Text.of("G"), textRenderer);
        this.bEntry = new IntegerField(0, 0, 21, 0, 255, 0, Text.of("B"), textRenderer);

        this.hEntry.setMaxLength(3);
        this.sEntry.setMaxLength(3);
        this.vEntry.setMaxLength(3);
        this.rEntry.setMaxLength(3);
        this.gEntry.setMaxLength(3);
        this.bEntry.setMaxLength(3);

        this.hEntry.setValueEnteredCallback(this::hueEntryListener);
        this.sEntry.setValueEnteredCallback(this::saturationEntryListener);
        this.vEntry.setValueEnteredCallback(this::brightnessEntryListener);
        this.rEntry.setValueEnteredCallback(this::redEntryListener);
        this.gEntry.setValueEnteredCallback(this::greenEntryListener);
        this.bEntry.setValueEnteredCallback(this::blueEntryListener);

        labelCol.addChild(hLabel);
        labelCol.addChild(sLabel);
        labelCol.addChild(vLabel);
        labelCol.addChild(rLabel);
        labelCol.addChild(gLabel);
        labelCol.addChild(bLabel);

        pickerCol.addChild(this.hsvColorPicker);
        pickerCol.addChild(this.rgbColorPicker);

        integerFieldCol.addChild(this.hEntry);
        integerFieldCol.addChild(this.sEntry);
        integerFieldCol.addChild(this.vEntry);
        integerFieldCol.addChild(this.rEntry);
        integerFieldCol.addChild(this.gEntry);
        integerFieldCol.addChild(this.bEntry);
        integerFieldCol.setXOffset(2);


        // bright colors first
        for (int i = 0; i < 8; i++) {
            int color = VANILLA_COLORS[i];
            ColorSquareButton colorBtn = new ColorSquareButton(0, 0, 10, color, false, () -> {
            });
            if (i == 0) {
                colorRow1.addClickable(colorBtn, 0);
            } else {
                colorRow1.addClickable(colorBtn);
            }
            colorBtn.setCallback(() -> {
                this.setColor(color);
                this.updateFocused(colorBtn);
            });
        }

        for (int i = 8; i < 16; i++) {
            int color = VANILLA_COLORS[i];
            ColorSquareButton colorBtn = new ColorSquareButton(0, 0, 10, color, false, () -> {
            });
            if (i == 8) {
                colorRow0.addClickable(colorBtn, 0);
            } else {
                colorRow0.addClickable(colorBtn);
            }
            colorBtn.setCallback(() -> {
                this.setColor(color);
                this.updateFocused(colorBtn);
            });
        }

//        // dye colors
//        int i = 0;
//        for (DyeColor dyeColor : DyeColor.values()) {
//            int color = dyeColor.getEntityColor();
//            ColorSquareButton colorBtn = new ColorSquareButton(0, 0, 10, color, false, () -> {});
//            if (i == 0) {
//                colorRow2.addClickable(colorBtn, 1);
//            } else {
//                colorRow2.addClickable(colorBtn);
//            }
//            colorBtn.setCallback(() -> {
//                this.setColor(color);
//                this.updateFocused(colorBtn);
//            });
//            i++;
//        }

        RandomColorSquareButton randomColorBtn = new RandomColorSquareButton(0, 0, 10, false, () -> {
        });
        randomColorBtn.setTooltip(Tooltip.of(Text.of("🎲")));
        colorRow1.addClickable(randomColorBtn);
        randomColorBtn.setCallback(() -> {
            this.setColor(randomColorBtn.getColor());
            this.updateFocused(randomColorBtn);
        });

        this.currentColorButton.setYOffset(-12);
        this.previousColorButton.setYOffset(-12);
        this.currentColorButton.setTooltip(Tooltip.of(Text.translatable("waypoint.edit.screen.current_color.hover")));
        this.previousColorButton.setTooltip(Tooltip.of(Text.translatable("waypoint.edit.screen.previous_color.hover")));
        this.currentColorButton.setCallback(() -> {
            updateFocused(this.currentColorButton);
            int currentColor = this.currentColorButton.getColor();
            int previousColor = this.previousColorButton.getColor();
            this.previousColorButton.setColor(currentColor);
            if (currentColor == previousColor) {
                this.confirmCallback.onColorUpdate(currentColor);
            }
        });
        this.previousColorButton.setCallback(() -> {
            updateFocused(this.previousColorButton);
            this.setColor(this.previousColorButton.getColor());
        });
        colorRow1.addClickable(this.currentColorButton, 9);
        colorRow1.addClickable(this.previousColorButton);

        slidersRow.addChild(labelCol, 0);
        slidersRow.addChild(pickerCol, 3);
        slidersRow.addChild(integerFieldCol, 1);

        this.mainLayout.addChild(slidersRow);
        this.mainLayout.addChild(colorRow0, 3);
        this.mainLayout.addChild(colorRow1, 1);
//        this.mainLayout.addChild(colorRow2, 2);

        this.width = this.mainLayout.getWidth();
        this.height = this.mainLayout.getHeight() - 11; // minus the extra height introduced by the current color button
    }

    public void setPreviousColor(int rgb) {
        this.previousColorButton.setColor(rgb);
    }

    private void hsvColorPickerCallback(int color) {
        this.rgbColorPicker.setColor(color);
        this.currentColorButton.setColor(color);
        this.setHSVEntryValues(this.hsvColorPicker.getSlider0Level(), this.hsvColorPicker.getSlider1Level(), this.hsvColorPicker.getSlider2Level());
        this.setRGBEntryValues(this.rgbColorPicker.getSlider0Level(), this.rgbColorPicker.getSlider1Level(), this.rgbColorPicker.getSlider2Level());
    }

    private void rgbColorPickerCallback(int color) {
        this.hsvColorPicker.setColor(color);
        this.currentColorButton.setColor(color);
        this.setHSVEntryValues(this.hsvColorPicker.getSlider0Level(), this.hsvColorPicker.getSlider1Level(), this.hsvColorPicker.getSlider2Level());
        this.setRGBEntryValues(this.rgbColorPicker.getSlider0Level(), this.rgbColorPicker.getSlider1Level(), this.rgbColorPicker.getSlider2Level());
    }

    private void setHSVEntryValues(int hue, int saturation, int brightness) {
        this.hEntry.setText(Integer.toString(hue));
        this.sEntry.setText(Integer.toString(saturation));
        this.vEntry.setText(Integer.toString(brightness));
    }

    private void setRGBEntryValues(int red, int green, int blue) {
        this.rEntry.setText(Integer.toString(red));
        this.gEntry.setText(Integer.toString(green));
        this.bEntry.setText(Integer.toString(blue));
    }

    private void hueEntryListener(int hue) {
        if (this.hEntry.isFocused()) {
            this.hsvColorPicker.updateSlider0(hue);
            int color = this.hsvColorPicker.getColor();
            this.rgbColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setRGBEntryValues(this.rgbColorPicker.getSlider0Level(), this.rgbColorPicker.getSlider1Level(), this.rgbColorPicker.getSlider2Level());
        }
    }

    private void saturationEntryListener(int saturation) {
        if (this.sEntry.isFocused()) {
            this.hsvColorPicker.updateSlider1(saturation);
            int color = this.hsvColorPicker.getColor();
            this.rgbColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setRGBEntryValues(this.rgbColorPicker.getSlider0Level(), this.rgbColorPicker.getSlider1Level(), this.rgbColorPicker.getSlider2Level());
        }
    }

    private void brightnessEntryListener(int brightness) {
        if (this.vEntry.isFocused()) {
            this.hsvColorPicker.updateSlider2(brightness);
            int color = this.hsvColorPicker.getColor();
            this.rgbColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setRGBEntryValues(this.rgbColorPicker.getSlider0Level(), this.rgbColorPicker.getSlider1Level(), this.rgbColorPicker.getSlider2Level());
        }
    }

    private void redEntryListener(int red) {
        if (this.rEntry.isFocused()) {
            this.rgbColorPicker.updateSlider0(red);
            int color = this.rgbColorPicker.getColor();
            this.hsvColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setHSVEntryValues(this.hsvColorPicker.getSlider0Level(), this.hsvColorPicker.getSlider1Level(), this.hsvColorPicker.getSlider2Level());
        }
    }

    private void greenEntryListener(int green) {
        if (this.gEntry.isFocused()) {
            this.rgbColorPicker.updateSlider1(green);
            int color = this.rgbColorPicker.getColor();
            this.hsvColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setHSVEntryValues(this.hsvColorPicker.getSlider0Level(), this.hsvColorPicker.getSlider1Level(), this.hsvColorPicker.getSlider2Level());
        }
    }

    private void blueEntryListener(int blue) {
        if (this.bEntry.isFocused()) {
            this.rgbColorPicker.updateSlider2(blue);
            int color = this.rgbColorPicker.getColor();
            this.hsvColorPicker.setColor(color);
            this.currentColorButton.setColor(color);
            this.setHSVEntryValues(this.hsvColorPicker.getSlider0Level(), this.hsvColorPicker.getSlider1Level(), this.hsvColorPicker.getSlider2Level());
        }
    }

    public void updateFocused(Element focused) {
        this.focused.setFocused(false);
        this.focused = focused;
        this.focused.setFocused(true);
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
    public void setXOffset(int x) {
        super.setXOffset(x);
        this.mainLayout.setXOffset(x);
    }

    @Override
    public void setYOffset(int y) {
        super.setYOffset(y);
        this.mainLayout.setYOffset(y);
    }

    @Override
    public int getWidth() {
        return this.mainLayout.getWidth();
    }

    @Override
    public int getHeight() {
        return this.mainLayout.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.visible) {
//            float x = (float) mouseX - getX();
            int pickerL = this.hsvColorPicker.getX();
            int pickerB = this.rgbColorPicker.getY() + this.hsvColorPicker.getHeight();
            if (mouseX < pickerL || mouseY > pickerB) {
                // not in the range of color pickers and integer entries
                return this.colorRow0.mouseClicked(mouseX, mouseY, button) || this.colorRow1.mouseClicked(mouseX, mouseY, button);
            } else {
                // in the range of color pickers and integer entries
                float y = (float) mouseY - getY();
                int pickerR = pickerL + this.hsvColorPicker.getWidth();
                if (mouseX >= pickerL && mouseX <= pickerR) {
                    int index = (int) Math.floor(y / this.hsvColorPicker.getHeight());
                    switch (index) {
                        case 0 -> {
                            this.hsvColorPicker.mouseClicked(mouseX, mouseY, button);
                            updateFocused(this.hsvColorPicker);
                            return true;
                        }
                        case 1 -> {
                            this.rgbColorPicker.mouseClicked(mouseX, mouseY, button);
                            updateFocused(this.rgbColorPicker);
                            return true;
                        }
                        default -> {return false;}
                    }
                }
                int entryL = this.hEntry.getX();
                int entryR = entryL + this.hEntry.getWidth();
                if (mouseX >= entryL && mouseX <= entryR) {
                    int index = (int) Math.floor(y / this.hEntry.getVisualHeight());
                    switch (index) {
                        case 0 -> {
                            updateFocused(this.hEntry);
                            this.hEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        case 1 -> {
                            updateFocused(this.sEntry);
                            this.sEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        case 2 -> {
                            updateFocused(this.vEntry);
                            this.vEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        case 3 -> {
                            updateFocused(this.rEntry);
                            this.rEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        case 4 -> {
                            updateFocused(this.gEntry);
                            this.gEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        case 5 -> {
                            updateFocused(this.bEntry);
                            this.bEntry.mouseClicked(mouseX, mouseY, button);
                            return true;
                        }
                        default -> {
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.focused instanceof Abstract3ChannelColorPicker<?> picker)
            return picker.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        else return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.focused.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.focused.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.hsvColorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) || this.rgbColorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
//        return this.focused.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public int getColor() {
        return this.rgbColorPicker.getColor();
    }

    @Override
    public void setColor(int color) {
        int rgb = 0xFF000000 | color;
        this.currentColorButton.setColor(rgb);
        this.rgbColorPicker.setColor(rgb);
        this.hsvColorPicker.setColor(rgb);
        this.rEntry.setText(Integer.toString(this.rgbColorPicker.getSlider0Level()));
        this.gEntry.setText(Integer.toString(this.rgbColorPicker.getSlider1Level()));
        this.bEntry.setText(Integer.toString(this.rgbColorPicker.getSlider2Level()));
        this.hEntry.setText(Integer.toString(this.hsvColorPicker.getSlider0Level()));
        this.sEntry.setText(Integer.toString(this.hsvColorPicker.getSlider1Level()));
        this.vEntry.setText(Integer.toString(this.hsvColorPicker.getSlider2Level()));
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (this.visible) {
            int x = getX() - BG_PADDING_X;
            int y = getY() - BG_PADDING_Y;
            int paddingWidth = BG_PADDING_X << 1;
            int paddingHeight = BG_PADDING_Y << 1;
            context.fill(x, y, x + this.width + paddingWidth, y + this.height + paddingHeight, TRANSPARENT_BG_COLOR);
            context.drawBorder(x, y, this.width + paddingWidth, this.height + paddingHeight, 0xFFFFFFFF);
            this.mainLayout.render(context, mouseX, mouseY, deltaTicks);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
