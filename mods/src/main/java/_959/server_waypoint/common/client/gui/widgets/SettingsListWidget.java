//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.Padding;
import _959.server_waypoint.common.client.gui.layout.SettingsListLayout;
import _959.server_waypoint.common.client.gui.layout.VisualPositioning;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * A scrollable panel of settings: section headers, and rows made of a label, a control, an optional
 * unit and an optional action. Entries have their own heights, so long labels wrap. The row widgets
 * stay registered with the screen; see the GUI guide's "Settings lists" section for the contract.
 */
public class SettingsListWidget extends ShiftableScrollableWidget implements Padding, Expandable {
    /** Space between the panel outline and the rows, on every side. */
    public static final int PANEL_PADDING = 6;
    private static final int SCROLLBAR_GAP = 2;
    private static final int SCROLL_STEP = 10;
    private static final long TOOLTIP_DELAY_NANOS = 500_000_000L;
    private static final int HEADER_LINE_GAP = 4;
    private static final int MIN_HEADER_LINE = 8;

    private final Font font;
    private final PaddingBackground panel;
    private List<Entry> entries = List.of();
    private int[] entryTops = {0};
    private int[] entryHeights = new int[0];
    private int suffixColumnWidth;
    private int actionColumnWidth;
    private int contentHeight;
    private @Nullable Row tooltipRow;
    private long tooltipRowSince;

    public SettingsListWidget(Font font) {
        super(0, 0, 0, 0, Component.empty());
        this.font = Objects.requireNonNull(font, "font");
        this.panel = new PaddingBackground(this, PANEL_PADDING, PANEL_PADDING, PANEL_BACKGROUND, BORDER, true);
    }

    /** Replaces the entries and lays them out. */
    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.relayout();
    }

    /** Lays the entries out again, after a label, unit or control size changes. */
    public void relayout() {
        this.suffixColumnWidth = 0;
        this.actionColumnWidth = 0;
        for (Entry entry : this.entries) {
            if (entry instanceof Row row) {
                this.suffixColumnWidth = Math.max(this.suffixColumnWidth, row.suffixWidth(this.font));
                this.actionColumnWidth = Math.max(this.actionColumnWidth, row.actionWidth());
            }
        }
        int rowWidth = this.rowWidth();
        this.entryHeights = new int[this.entries.size()];
        boolean[] headers = new boolean[this.entries.size()];
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            this.entryHeights[i] = entry.layout(this, rowWidth);
            headers[i] = entry instanceof Header;
        }
        this.entryTops = SettingsListLayout.entryTops(this.entryHeights, headers);
        this.contentHeight = this.entryTops[this.entries.size()];
        this.refreshScroll();
    }

    /** The width at which no entry wraps, including the always-reserved scrollbar column. */
    public int getPreferredWidth() {
        int widest = 0;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, entry.preferredWidth(this));
        }
        return widest + this.SCROLLBAR_WIDTH + SCROLLBAR_GAP;
    }

    @Override
    public int getContentHeight() {
        return this.contentHeight;
    }

    @Override
    public double getDeltaYPerScroll() {
        return SCROLL_STEP;
    }

    @Override
    public void setScrollY(double scrollY) {
        super.setScrollY(scrollY);
        this.positionEntries();
    }

    /**
     * Scrolls the least amount that fully shows the row owning {@code widget} and, when they fit, the
     * nearest rows above and below that have an active widget. Does nothing for other listeners.
     */
    public void reveal(GuiEventListener widget) {
        int index = this.rowIndexOf(widget);
        if (index < 0) {
            return;
        }
        int previous = this.interactiveRowBefore(index);
        int next = this.interactiveRowAfter(index);
        int previousTop = previous < 0 ? 0 : this.entryTops[previous];
        int nextBottom = next < 0 ? this.contentHeight : this.entryBottom(next);
        this.setScrollY(SettingsListLayout.revealScroll(this.getScrollY(), this.height, this.contentHeight,
                this.entryTops[index], this.entryBottom(index), previousTop, nextBottom));
    }

    /**
     * Before {@code screen} handles Tab, or Shift-Tab when {@code forward} is false: when the next
     * stop in its Tab order, wrapping around, is one of this list's hidden row widgets, scrolls it
     * into view. Vanilla skips hidden widgets, so Tab would otherwise jump past the rows out of view.
     */
    public void revealTabTarget(ContainerEventHandler screen, boolean forward) {
        List<GuiEventListener> order = new ArrayList<>(screen.children());
        // Vanilla's Tab order: the children sorted by tab order group, keeping their order in a group.
        order.sort(Comparator.comparingInt(GuiEventListener::getTabOrderGroup));
        int size = order.size();
        int focused = order.indexOf(screen.getFocused());
        int start = focused >= 0 ? focused : forward ? -1 : size;
        FocusNavigationEvent.TabNavigation tab = new FocusNavigationEvent.TabNavigation(forward);
        for (int step = 1; step <= size; step++) {
            GuiEventListener candidate = order.get(Math.floorMod(start + (forward ? step : -step), size));
            if (candidate instanceof AbstractWidget widget && this.rowIndexOf(widget) >= 0) {
                if (widget.active) {
                    if (!widget.visible) {
                        this.reveal(widget);
                    }
                    return;
                }
            } else if (candidate.nextFocusPath(tab) != null) {
                return;
            }
        }
    }

    /** Visits every row's control and action in entry order, then the list itself. */
    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        for (Entry entry : this.entries) {
            entry.visitWidgets(consumer);
        }
        consumer.accept(this);
    }

    /** The list itself isn't a Tab stop; the widgets in its rows are. */
    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    /** Only the scrollbar reacts to clicks; clicks on empty list space do nothing. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.overflows() || !this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.positionEntries();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.positionEntries();
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        this.positionEntries();
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        this.positionEntries();
    }

    @Override
    public void setWidth(int width) {
        if (this.width != width) {
            this.width = width;
            this.relayout();
        }
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.refreshScroll();
    }

    @Override
    public void setVisualWidth(int width) {
        this.setWidth(width - PANEL_PADDING * 2);
    }

    @Override
    public void setVisualHeight(int height) {
        this.setHeight(height - PANEL_PADDING * 2);
    }

    @Override
    public int getVisualX() {
        return this.panel.getVisualX();
    }

    @Override
    public int getVisualY() {
        return this.panel.getVisualY();
    }

    @Override
    public int getVisualWidth() {
        return this.panel.getVisualWidth();
    }

    @Override
    public int getVisualHeight() {
        return this.panel.getVisualHeight();
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.panel.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        int x = this.getX();
        int y = this.getY();
        int bottom = y + this.height;
        int rowWidth = this.rowWidth();
        boolean pointerInside = this.active
                && mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < bottom;
        Row hovered = null;
        context.enableScissor(x, y, x + this.width, bottom);
        for (int i = 0; i < this.entries.size(); i++) {
            int top = y + this.entryTops[i] - (int) this.getScrollY();
            int entryBottom = top + this.entryHeights[i];
            if (entryBottom <= y || top >= bottom) {
                continue;
            }
            Entry entry = this.entries.get(i);
            if (pointerInside && mouseY >= top && mouseY < entryBottom && entry instanceof Row row) {
                hovered = row;
                context.fill(x, top, x + rowWidth, entryBottom, getColor(ROW_HOVER_BACKGROUND));
            }
            entry.renderEntry(this, context, mouseX, mouseY, deltaTicks);
        }
        context.disableScissor();
        this.drawScrollbar(context);
        this.scheduleTooltip(context, hovered, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    private void scheduleTooltip(GuiGraphicsExtractor context, @Nullable Row hovered, int mouseX, int mouseY) {
        if (hovered == null || hovered.isOverAction(mouseX, mouseY)) {
            this.tooltipRow = null;
            return;
        }
        long now = System.nanoTime();
        if (hovered != this.tooltipRow) {
            this.tooltipRow = hovered;
            this.tooltipRowSince = now;
            return;
        }
        if (now - this.tooltipRowSince < TOOLTIP_DELAY_NANOS) {
            return;
        }
        Component text = hovered.tooltipText();
        if (text == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        var lines = Tooltip.create(text).toCharSequence(client);
        // Anchor the row's tooltip to the cursor, not the whole scrollable list.
        //? if >=1.21.6 {
        context.setTooltipForNextFrame(lines, mouseX, mouseY);
        //?} else {
        /*if (client.screen != null) client.screen.setTooltipForNextRenderPass(lines);
        *///?}
    }

    /**
     * Draws one part of an entry. A row widget that isn't fully inside the viewport is invisible to
     * input; it's drawn anyway, clipped by the scissor, with the mouse moved off-screen. The hidden
     * widgets a part visits, such as some of a composite control's, are made visible for one draw of
     * the whole part, so its other widgets get the off-screen mouse too and show no hover state.
     */
    static void renderPart(GuiGraphicsExtractor context, Renderable part, int mouseX, int mouseY, float deltaTicks) {
        List<AbstractWidget> hidden = part instanceof LayoutElement element ? HiddenWidgets.visitedBy(element) : null;
        if (hidden != null) {
            hidden.forEach(widget -> widget.visible = true);
            part.
            //$ render_method_swap
            extractRenderState
                    (context, NO_MOUSE, NO_MOUSE, deltaTicks);
            hidden.forEach(widget -> widget.visible = false);
            return;
        }
        part.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    private void positionEntries() {
        int top = this.getY() - (int) this.getScrollY();
        int viewportTop = this.getY();
        int viewportBottom = this.getY() + this.height;
        for (int i = 0; i < this.entries.size(); i++) {
            this.entries.get(i).position(this.getX(), top + this.entryTops[i], viewportTop, viewportBottom);
        }
    }

    private int rowIndexOf(GuiEventListener widget) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i) instanceof Row row && row.owns(widget)) {
                return i;
            }
        }
        return -1;
    }

    private int interactiveRowBefore(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (this.entries.get(i) instanceof Row row && row.isInteractive()) {
                return i;
            }
        }
        return -1;
    }

    private int interactiveRowAfter(int index) {
        for (int i = index + 1; i < this.entries.size(); i++) {
            if (this.entries.get(i) instanceof Row row && row.isInteractive()) {
                return i;
            }
        }
        return -1;
    }

    private int entryBottom(int index) {
        return this.entryTops[index] + this.entryHeights[index];
    }

    private int rowWidth() {
        return Math.max(0, this.width - this.SCROLLBAR_WIDTH - SCROLLBAR_GAP);
    }

    /** An entry of the list: a {@link Header} or a {@link Row}. */
    public abstract static sealed class Entry permits Header, Row {
        private Entry() {
        }

        /** Lays the entry out for {@code rowWidth} and returns its height. */
        abstract int layout(SettingsListWidget list, int rowWidth);

        abstract void position(int x, int y, int viewportTop, int viewportBottom);

        abstract void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                                  int mouseX, int mouseY, float deltaTicks);

        abstract int preferredWidth(SettingsListWidget list);

        void visitWidgets(Consumer<AbstractWidget> consumer) {
        }
    }

    /** A section title followed by a line across the rest of the width. */
    public static final class Header extends Entry {
        private final Component title;
        private @Nullable ScalableText titleText;
        private int x;
        private int y;
        private int width;

        public Header(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        @Override
        int layout(SettingsListWidget list, int rowWidth) {
            int wrapWidth = Math.max(1, rowWidth);
            if (this.titleText == null) {
                this.titleText = new ScalableText(0, 0, this.title, 1.0F, TEXT_PRIMARY, wrapWidth, list.font);
            } else {
                this.titleText.setMaxWidth(wrapWidth);
            }
            this.width = rowWidth;
            return SettingsListLayout.headerHeight(this.titleText.getHeight());
        }

        @Override
        void position(int x, int y, int viewportTop, int viewportBottom) {
            this.x = x;
            this.y = y;
            if (this.titleText != null) {
                this.titleText.setPosition(x, y);
            }
        }

        @Override
        void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                         int mouseX, int mouseY, float deltaTicks) {
            if (this.titleText == null) {
                return;
            }
            renderPart(context, this.titleText, mouseX, mouseY, deltaTicks);
            int lineStart = this.x + list.font.width(this.title) + HEADER_LINE_GAP;
            int lineEnd = this.x + this.width;
            if (this.titleText.getHeight() <= list.font.lineHeight && lineEnd - lineStart >= MIN_HEADER_LINE) {
                int lineY = this.y + list.font.lineHeight / 2;
                context.fill(lineStart, lineY, lineEnd, lineY + 1, getColor(BORDER));
            }
        }

        @Override
        int preferredWidth(SettingsListWidget list) {
            return list.font.width(this.title) + HEADER_LINE_GAP + MIN_HEADER_LINE;
        }
    }

    /** A label on the left, then a control, an optional unit and an optional action on the right. */
    public static final class Row extends Entry {
        private final Component label;
        private final LayoutElement control;
        private final Renderable controlRenderer;
        private @Nullable Component suffix;
        private @Nullable AbstractWidget action;
        private @Nullable Supplier<Component> tooltip;
        private WidgetThemeVariable labelColor = TEXT_PRIMARY;
        private @Nullable ScalableText labelText;
        private @Nullable ScalableText suffixText;
        private WidgetPack pack = new WidgetPack(Orientation.HORIZONTAL);

        public <C extends LayoutElement & Renderable> Row(Component label, C control) {
            this.label = Objects.requireNonNull(label, "label");
            this.control = Objects.requireNonNull(control, "control");
            this.controlRenderer = control;
        }

        /** A muted unit after the control, such as "%" or "chunks". */
        public Row suffix(Component unit) {
            this.suffix = Objects.requireNonNull(unit, "unit");
            return this;
        }

        /** A widget in the last column, such as a reset button. */
        public Row action(AbstractWidget action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        /** The hover text. It's read each time the tooltip shows, so it can follow the current state. */
        public Row tooltip(Supplier<Component> tooltip) {
            this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
            return this;
        }

        public Row labelColor(WidgetThemeVariable color) {
            this.labelColor = Objects.requireNonNull(color, "color");
            return this;
        }

        int suffixWidth(Font font) {
            return this.suffix == null ? 0 : font.width(this.suffix);
        }

        int actionWidth() {
            return this.action == null ? 0 : VisualPositioning.getVisualWidth(this.action);
        }

        boolean owns(GuiEventListener widget) {
            return this.widgets().contains(widget);
        }

        /** Whether Tab can stop in this row now: it has an active widget, and vanilla skips inactive ones. */
        boolean isInteractive() {
            return this.widgets().stream().anyMatch(widget -> widget.active);
        }

        /** The control's widgets, several for a composite control, then the action. */
        private List<AbstractWidget> widgets() {
            List<AbstractWidget> widgets = new ArrayList<>();
            this.visitWidgets(widgets::add);
            return widgets;
        }

        boolean isOverAction(int mouseX, int mouseY) {
            if (this.action == null) {
                return false;
            }
            int x = VisualPositioning.getVisualX(this.action);
            int y = VisualPositioning.getVisualY(this.action);
            return mouseX >= x && mouseX < x + VisualPositioning.getVisualWidth(this.action)
                    && mouseY >= y && mouseY < y + VisualPositioning.getVisualHeight(this.action);
        }

        @Nullable Component tooltipText() {
            return this.tooltip == null ? null : this.tooltip.get();
        }

        @Override
        int layout(SettingsListWidget list, int rowWidth) {
            int suffixColumn = list.suffixColumnWidth;
            int actionColumn = list.actionColumnWidth;
            int labelWidth = SettingsListLayout.labelWidth(rowWidth, VisualPositioning.getVisualWidth(this.control), suffixColumn, actionColumn);
            if (this.labelText == null) {
                this.labelText = new ScalableText(0, 0, this.label, 1.0F, this.labelColor, labelWidth, list.font);
            } else {
                this.labelText.setMaxWidth(labelWidth);
                this.labelText.setColor(this.labelColor);
            }
            if (this.suffix != null) {
                if (this.suffixText == null) {
                    this.suffixText = new ScalableText(0, 0, this.suffix, 1.0F, TEXT_MUTED, suffixColumn, list.font);
                } else {
                    this.suffixText.setMaxWidth(suffixColumn);
                }
            }
            int actionHeight = this.action == null ? 0 : VisualPositioning.getVisualHeight(this.action);
            int height = SettingsListLayout.rowHeight(
                    Math.max(this.labelText.getHeight(), Math.max(VisualPositioning.getVisualHeight(this.control), actionHeight)));

            WidgetPack pack = new WidgetPack(0, 0, rowWidth, height, Orientation.HORIZONTAL);
            pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
            pack.addChild(this.labelText, Direction.FORWARD);
            if (actionColumn > 0) {
                int actionWidth = this.actionWidth();
                if (this.action != null) {
                    pack.addChild(this.action, Direction.REVERSE);
                }
                if (actionColumn > actionWidth) {
                    pack.addChild(SpacerElement.width(actionColumn - actionWidth), Direction.REVERSE);
                }
                pack.addChild(SpacerElement.width(SettingsListLayout.ACTION_GAP), Direction.REVERSE);
            }
            if (suffixColumn > 0) {
                if (this.suffixText != null) {
                    pack.addChild(this.suffixText, Direction.REVERSE);
                } else {
                    pack.addChild(SpacerElement.width(suffixColumn), Direction.REVERSE);
                }
                pack.addChild(SpacerElement.width(SettingsListLayout.SUFFIX_GAP), Direction.REVERSE);
            }
            pack.addChild(this.control, Direction.REVERSE);
            this.pack = pack;
            return height;
        }

        @Override
        void position(int x, int y, int viewportTop, int viewportBottom) {
            this.pack.setPosition(x, y);
            this.visitWidgets(widget -> {
                int top = VisualPositioning.getVisualY(widget);
                widget.visible = SettingsListLayout.fullyVisible(top, top + VisualPositioning.getVisualHeight(widget), viewportTop, viewportBottom);
            });
        }

        @Override
        void renderEntry(SettingsListWidget list, GuiGraphicsExtractor context,
                         int mouseX, int mouseY, float deltaTicks) {
            if (this.labelText == null) {
                return;
            }
            renderPart(context, this.labelText, mouseX, mouseY, deltaTicks);
            if (this.suffixText != null) {
                renderPart(context, this.suffixText, mouseX, mouseY, deltaTicks);
            }
            renderPart(context, this.controlRenderer, mouseX, mouseY, deltaTicks);
            if (this.action != null) {
                renderPart(context, this.action, mouseX, mouseY, deltaTicks);
            }
        }

        @Override
        int preferredWidth(SettingsListWidget list) {
            return SettingsListLayout.rowPreferredWidth(list.font.width(this.label), VisualPositioning.getVisualWidth(this.control),
                    list.suffixColumnWidth, list.actionColumnWidth);
        }

        @Override
        void visitWidgets(Consumer<AbstractWidget> consumer) {
            this.control.visitWidgets(consumer);
            if (this.action != null) {
                this.action.visitWidgets(consumer);
            }
        }
    }

    /**
     * Collects the hidden widgets an element visits. Every visible part is drawn each frame, and its
     * widgets are usually all visible, so the list is created only with the first hidden widget.
     */
    private static final class HiddenWidgets implements Consumer<AbstractWidget> {
        private @Nullable List<AbstractWidget> widgets;

        /** The hidden widgets {@code element} visits, or null when there are none. */
        static @Nullable List<AbstractWidget> visitedBy(LayoutElement element) {
            HiddenWidgets hidden = new HiddenWidgets();
            element.visitWidgets(hidden);
            return hidden.widgets;
        }

        @Override
        public void accept(AbstractWidget widget) {
            if (!widget.visible) {
                if (this.widgets == null) {
                    this.widgets = new ArrayList<>();
                }
                this.widgets.add(widget);
            }
        }
    }
}
