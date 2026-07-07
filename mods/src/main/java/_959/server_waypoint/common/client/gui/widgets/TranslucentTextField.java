//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
//? if >= 1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
import net.minecraft.network.chat.Component;

public class TranslucentTextField extends EditBox implements Shiftable, Expandable, Padding {
    static final int OUTLINE_PADDING = 2;
    private static final int SUGGESTION_LINE_HEIGHT = 12;
    private static final int MAX_VISIBLE_SUGGESTIONS = 5;

    private final Font textRenderer;
    private final AnchorMode anchorMode;
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;
    protected final int backgroundHeight;
    private Supplier<List<String>> suggestionsProvider = List::of;
    private List<String> suggestions = List.of();
    private int selectedSuggestion;
    private int suggestionOffset;
    private boolean tabCycles;
    private String tabCycleBaseValue = "";
    private int suggestionsX;
    private int suggestionsY;
    private int suggestionsWidth;
    private int suggestionsHeight;
    private int lastSuggestionMouseX = Integer.MIN_VALUE;
    private int lastSuggestionMouseY = Integer.MIN_VALUE;
    private String inlineSuggestion;

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
        this(x, y, width, text, textRenderer, AnchorMode.CONTENT);
    }

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer, AnchorMode anchorMode) {
        super(
                textRenderer,
                AnchorMode.normalize(anchorMode).getContentX(x, OUTLINE_PADDING),
                AnchorMode.normalize(anchorMode).getContentY(y, OUTLINE_PADDING),
                width,
                textRenderer.lineHeight,
                null,
                text
        );
        this.textRenderer = textRenderer;
        this.anchorMode = AnchorMode.normalize(anchorMode);
        this.setTextColor(0xFFFFFFFF);
        this.setBordered(false);
        this.backgroundHeight = this.height + 2;
        this.setX(x);
        this.setY(y);
    }

    public void setSuggestionsProvider(Supplier<List<String>> suggestionsProvider) {
        this.suggestionsProvider = suggestionsProvider == null ? List::of : suggestionsProvider;
        if (this.isFocused()) {
            this.updateSuggestions();
        }
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        context.fill(x + 1, y + 1, right, bottom, BUTTON_BG_COLOR);
        this.isHovered = mouseX >= x && mouseY >= y && mouseX <= right && mouseY <= bottom;
        int bdColor = isFocused() | isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
        this.renderTextField(context, mouseX, mouseY, deltaTicks);
    }

    public void renderTextField(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.
        //$ render_widget_method_swap
        extractWidgetRenderState
        (context, mouseX, mouseY, deltaTicks);
        this.renderInlineSuggestion(context);
    }

    public void renderSuggestions(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!this.isSuggestionListVisible()) {
            return;
        }
        this.updateSuggestionBounds();
        this.updateHoveredSuggestion(mouseX, mouseY);

        int visibleSuggestions = Math.min(this.suggestions.size(), MAX_VISIBLE_SUGGESTIONS);
        context.fill(this.suggestionsX, this.suggestionsY, this.suggestionsX + this.suggestionsWidth, this.suggestionsY + this.suggestionsHeight, BUTTON_BG_COLOR);
        for (int i = 0; i < visibleSuggestions; i++) {
            int suggestionIndex = i + this.suggestionOffset;
            String suggestion = this.suggestions.get(suggestionIndex);
            int y = this.suggestionsY + i * SUGGESTION_LINE_HEIGHT;
            int color = suggestionIndex == this.selectedSuggestion ? 0xFFFFFF00 : 0xFFAAAAAA;
            drawText(context, this.textRenderer, this.textRenderer.plainSubstrByWidth(suggestion, this.suggestionsWidth - 2), this.getTextAnchorX(), y + 2, color, true);
        }
    }

    public boolean mouseClickedSuggestion(double mouseX, double mouseY) {
        return this.handleSuggestionMouseClicked(mouseX, mouseY);
    }

    @Override
    public int getVisualHeight() {
        return this.backgroundHeight;
    }

    @Override
    public int getVisualWidth() {
        return this.width;
    }

    @Override
    public int getVisualX() {
        return getX() - OUTLINE_PADDING;
    }

    @Override
    public int getVisualY() {
        return getY() - OUTLINE_PADDING;
    }

    @Override
    public int getHeight() {
        return this.backgroundHeight;
    }

    @Override
    public void setVisualHeight(int height) {
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int height) {
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.handleSuggestionKey(keyCode, modifiers)) {
            return true;
        }
        //? if >= 1.21.9 {
        boolean handled = super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
        //?} else {
        /*boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        *///?}
        if (handled) {
            this.tabCycles = false;
            this.updateSuggestions();
        }
        return handled;
    }

    public boolean charTyped(char chr, int modifiers) {
        //? if >= 1.21.9 {
        boolean handled = super.charTyped(new CharacterEvent(chr/*? if <26 {*//*, modifiers*//*?}*/));
        //?} else {
        /*boolean handled = super.charTyped(chr, modifiers);
        *///?}
        if (handled) {
            this.tabCycles = false;
            this.updateSuggestions();
        }
        return handled;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handleSuggestionMouseClicked(mouseX, mouseY)) {
            return true;
        }
        //? if >= 1.21.9 {
        boolean handled = super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        //?} else {
        /*boolean handled = super.mouseClicked(mouseX, mouseY, button);
        *///?}
        if (handled) {
            this.tabCycles = false;
            this.updateSuggestions();
        }
        return handled;
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        this.tabCycles = false;
        this.updateSuggestions();
    }

    @Override
    public void insertText(String text) {
        super.insertText(text);
        this.tabCycles = false;
        this.updateSuggestions();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.updateSuggestions();
        } else {
            this.hideSuggestions();
        }
    }

    @Override
    public void setSuggestion(String suggestion) {
        this.inlineSuggestion = suggestion;
        super.setSuggestion(null);
    }

    //? if >= 1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return this.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        return this.charTyped(
                characterEvent.codepointAsString().charAt(0),
                /*? if >=26 {*/ 0 /*?} else {*/ /*characterEvent.modifiers() *//*?}*/
        );
    }
    //?}

    @Override
    public int getX() {
        return this.shiftedX;
    }

    @Override
    public int getY() {
        return this.shiftedY;
    }

    @Override
    public void setX(int x) {
        this.shiftedX = this.anchorMode.getContentX(x + this.xOffset, OUTLINE_PADDING);
        super.setX(this.anchorMode.getContentX(x, OUTLINE_PADDING));
    }

    @Override
    public void setY(int y) {
        this.shiftedY = this.anchorMode.getContentY(y + this.yOffset, OUTLINE_PADDING);
        super.setY(this.anchorMode.getContentY(y, OUTLINE_PADDING));
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        int anchorX = this.anchorMode.getAnchorX(super.getX(), OUTLINE_PADDING);
        this.shiftedX = this.anchorMode.getContentX(anchorX + x, OUTLINE_PADDING);
        super.setX(super.getX());
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        int anchorY = this.anchorMode.getAnchorY(super.getY(), OUTLINE_PADDING);
        this.shiftedY = this.anchorMode.getContentY(anchorY + y, OUTLINE_PADDING);
        super.setY(super.getY());
    }

    @Override
    public int getShiftedX() {
        return this.shiftedX;
    }

    @Override
    public int getShiftedY() {
        return this.shiftedY;
    }

    private boolean handleSuggestionKey(int keyCode, int modifiers) {
        if (!this.isFocused()) {
            return false;
        }
        if (keyCode == GLFW_KEY_ESCAPE && this.isSuggestionListVisible()) {
            this.hideSuggestions();
            return true;
        }
        if (keyCode == GLFW_KEY_UP || keyCode == GLFW_KEY_DOWN) {
            this.updateSuggestions();
            if (this.suggestions.isEmpty()) {
                return false;
            }
            this.cycleSuggestion(keyCode == GLFW_KEY_UP ? -1 : 1);
            this.tabCycles = false;
            return true;
        }
        if (keyCode != GLFW_KEY_TAB) {
            return false;
        }
        if (!this.tabCycles) {
            this.updateSuggestions();
            this.tabCycleBaseValue = this.getValue();
        } else {
            this.cycleSuggestion(hasShiftDown(modifiers) ? -1 : 1);
        }
        if (this.suggestions.isEmpty()) {
            return false;
        }
        this.useSuggestion(this.tabCycleBaseValue);
        this.tabCycles = true;
        return true;
    }

    private boolean handleSuggestionMouseClicked(double mouseX, double mouseY) {
        if (!this.isSuggestionListVisible()) {
            return false;
        }
        this.updateSuggestionBounds();
        if (mouseX < this.suggestionsX || mouseX > this.suggestionsX + this.suggestionsWidth || mouseY < this.suggestionsY || mouseY > this.suggestionsY + this.suggestionsHeight) {
            return false;
        }
        int suggestionIndex = this.suggestionOffset + (int) ((mouseY - this.suggestionsY) / SUGGESTION_LINE_HEIGHT);
        if (suggestionIndex >= 0 && suggestionIndex < this.suggestions.size()) {
            this.selectedSuggestion = suggestionIndex;
            this.useSuggestion(this.getValue());
            this.tabCycles = false;
            this.updateSuggestions();
        }
        return true;
    }

    private void updateHoveredSuggestion(int mouseX, int mouseY) {
        if (mouseX == this.lastSuggestionMouseX && mouseY == this.lastSuggestionMouseY) {
            return;
        }
        this.lastSuggestionMouseX = mouseX;
        this.lastSuggestionMouseY = mouseY;
        if (mouseX < this.suggestionsX || mouseX > this.suggestionsX + this.suggestionsWidth || mouseY < this.suggestionsY || mouseY > this.suggestionsY + this.suggestionsHeight) {
            return;
        }
        int suggestionIndex = this.suggestionOffset + (mouseY - this.suggestionsY) / SUGGESTION_LINE_HEIGHT;
        if (suggestionIndex >= 0 && suggestionIndex < this.suggestions.size()) {
            this.selectedSuggestion = suggestionIndex;
            this.updateInlineSuggestion();
        }
    }

    private void updateSuggestions() {
        if (this.tabCycles) {
            return;
        }
        Set<String> uniqueSuggestions = new LinkedHashSet<>();
        for (String suggestion : this.suggestionsProvider.get()) {
            if (suggestion != null && !suggestion.isEmpty()) {
                uniqueSuggestions.add(suggestion);
            }
        }
        String value = this.getValue();
        String lowerValue = value.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>(uniqueSuggestions.size());
        for (String suggestion : uniqueSuggestions) {
            if (suggestion.equals(value)) {
                continue;
            }
            if (this.shouldShowSuggestion(suggestion, value, lowerValue)) {
                matches.add(suggestion);
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        this.suggestions = matches;
        this.selectedSuggestion = Math.min(this.selectedSuggestion, Math.max(this.suggestions.size() - 1, 0));
        this.suggestionOffset = Math.min(this.suggestionOffset, Math.max(this.suggestions.size() - MAX_VISIBLE_SUGGESTIONS, 0));
        this.ensureSelectedSuggestionVisible();
        this.updateInlineSuggestion();
    }

    protected boolean shouldShowSuggestion(String suggestion, String value, String lowerValue) {
        return lowerValue.isEmpty() || suggestion.toLowerCase(Locale.ROOT).startsWith(lowerValue);
    }

    private void updateSuggestionBounds() {
        int maxTextWidth = 0;
        for (String suggestion : this.suggestions) {
            maxTextWidth = Math.max(maxTextWidth, this.textRenderer.width(suggestion));
        }
        this.suggestionsX = this.getTextAnchorX() - 1;
        this.suggestionsY = this.getShiftedY() - 2 + this.backgroundHeight;
        this.suggestionsWidth = Math.max(this.getShiftedX() + this.width - this.suggestionsX - 2, maxTextWidth + 2);
        this.suggestionsHeight = Math.min(this.suggestions.size(), MAX_VISIBLE_SUGGESTIONS) * SUGGESTION_LINE_HEIGHT;
    }

    private boolean isSuggestionListVisible() {
        if (!this.isFocused()) {
            return false;
        }
        this.updateSuggestions();
        return !this.suggestions.isEmpty();
    }

    private void cycleSuggestion(int direction) {
        if (this.suggestions.isEmpty()) {
            return;
        }
        this.selectedSuggestion += direction;
        if (this.selectedSuggestion < 0) {
            this.selectedSuggestion = this.suggestions.size() - 1;
        } else if (this.selectedSuggestion >= this.suggestions.size()) {
            this.selectedSuggestion = 0;
        }
        this.ensureSelectedSuggestionVisible();
        this.updateInlineSuggestion();
    }

    private void ensureSelectedSuggestionVisible() {
        int lastVisibleSuggestion = this.suggestionOffset + MAX_VISIBLE_SUGGESTIONS - 1;
        if (this.selectedSuggestion < this.suggestionOffset) {
            this.suggestionOffset = this.selectedSuggestion;
        } else if (this.selectedSuggestion > lastVisibleSuggestion) {
            this.suggestionOffset = this.selectedSuggestion - MAX_VISIBLE_SUGGESTIONS + 1;
        }
        this.suggestionOffset = Math.max(this.suggestionOffset, 0);
    }

    private void useSuggestion(String baseValue) {
        if (this.suggestions.isEmpty()) {
            return;
        }
        String suggestion = this.suggestions.get(this.selectedSuggestion);
        super.setValue(suggestion);
        this.setCursorPosition(suggestion.length());
        this.setHighlightPos(suggestion.length());
        this.tabCycleBaseValue = baseValue;
        this.updateInlineSuggestion();
    }

    private void updateInlineSuggestion() {
        if (this.suggestions.isEmpty() || !this.isFocused()) {
            this.setSuggestion(null);
            return;
        }
        String value = this.getValue();
        String suggestion = this.suggestions.get(this.selectedSuggestion);
        this.setSuggestion(suggestion.startsWith(value) ? suggestion.substring(value.length()) : null);
    }

    private void renderInlineSuggestion(GuiGraphicsExtractor context) {
        if (this.inlineSuggestion == null || this.inlineSuggestion.isEmpty() || !this.isFocused() || this.getCursorPosition() != this.getValue().length()) {
            return;
        }
        drawText(context, this.textRenderer, this.inlineSuggestion, this.getInlineSuggestionX(), this.getShiftedY(), 0xFF808080, true);
    }

    private int getTextAnchorX() {
        return this.getShiftedX();
    }

    private int getInlineSuggestionX() {
        if (this.getValue().isEmpty()) {
            return this.getTextAnchorX();
        }
        return this.getTextAnchorX() + this.textRenderer.width(this.getValue());
    }

    private void hideSuggestions() {
        this.suggestions = List.of();
        this.selectedSuggestion = 0;
        this.suggestionOffset = 0;
        this.tabCycles = false;
        this.setSuggestion(null);
    }

    private static boolean hasShiftDown(int modifiers) {
        return (modifiers & 1) != 0;
    }
}
